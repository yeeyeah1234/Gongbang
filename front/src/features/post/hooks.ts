import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchContests } from '../contest/api'
import { useViewer } from '../contest/hooks'
import type { ContestStatus } from '../contest/types'
import {
  acceptApplication,
  applyToPost,
  cancelApplication,
  completePost,
  createPost,
  deletePost,
  fetchApplicants,
  fetchPost,
  fetchPosts,
  rejectApplication,
  updatePost,
} from './api'
import { savePostImages } from './postImages'
import type { ApplicantList, ApplicantStatus, PostDetail } from './types'

// PST-001 ⑦: 20개씩 페이지로 나눈다
export const POST_PAGE_SIZE = 20

export const postKeys = {
  all: ['posts'] as const,
  lists: () => [...postKeys.all, 'list'] as const,
  // contestId가 없으면 null (전체 목록)
  list: (page: number, contestId: number | null = null) =>
    [...postKeys.lists(), page, contestId] as const,
  // MAIN-02 ⑨ 공모전 상세의 「팀원 모집 중」 칸. lists 아래에 두어 글 작성·삭제·모집완료 때 함께 새로 받는다
  contestRecruiting: (contestId: number) =>
    [...postKeys.lists(), 'contest-recruiting', contestId] as const,
  details: () => [...postKeys.all, 'detail'] as const,
  detail: (postId: number, viewer: string) => [...postKeys.details(), postId, viewer] as const,
  // 지원자 목록도 posts 아래에 두어, 지원·취소·수락·거절 때 한 번에 다시 불러온다
  applicants: (postId: number) => [...postKeys.all, 'applicants', postId] as const,
  applicantTab: (postId: number, status: ApplicantStatus) =>
    [...postKeys.applicants(postId), status] as const,
}

// PST-001 모집글 목록. 페이지를 넘길 때 앞 페이지를 잠깐 그대로 보여 줘서 표가 깜빡이지 않게 한다.
// ⑩ contestId가 있으면 그 공모전 글만 (공모전 상세 「더 보기」)
export function usePostList(page: number, contestId: number | null = null) {
  return useQuery({
    queryKey: postKeys.list(page, contestId),
    queryFn: () =>
      fetchPosts({ page, size: POST_PAGE_SIZE, ...(contestId === null ? {} : { contestId }) }),
    placeholderData: keepPreviousData,
  })
}

// MAIN-02 ⑨ 공모전 상세 「팀원 모집 중」 칸에 보여 줄 개수
export const CONTEST_RECRUITING_SIZE = 3

// MAIN-02 ⑨: 이 공모전에서 지금 지원할 수 있는 모집글 최신 3개와 전체 개수(totalCount)
export function useContestRecruitingPosts(contestId: number) {
  return useQuery({
    queryKey: postKeys.contestRecruiting(contestId),
    queryFn: () =>
      fetchPosts({ page: 0, size: CONTEST_RECRUITING_SIZE, contestId, recruiting: true }),
    enabled: Number.isInteger(contestId) && contestId > 0,
  })
}

// PST-002 작성. 등록하면 목록을 새로 받게 한다.
// 다시 불러오기를 기다리지 않는다: 기다리면 그동안 요청이 「진행 중」으로 남아 화면 이동이 늦어진다 (테스트 서버는 왕복 약 0.5초)
export function useCreatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: postKeys.lists() })
    },
  })
}

// PST-002 ②: 진행 중인 공모전을 제목으로 검색 (최규선 MAIN-01 API, status=active)
const CONTEST_SEARCH_SIZE = 8

export function useContestSearch(keyword: string, status: ContestStatus = 'active') {
  return useQuery({
    queryKey: ['contest-search', status, keyword],
    enabled: keyword.length > 0,
    queryFn: () =>
      fetchContests({
        status,
        sort: 'deadline',
        keyword,
        page: 0,
        size: CONTEST_SEARCH_SIZE,
      }),
    placeholderData: keepPreviousData,
  })
}

// PST-005 상세. myStatus가 로그인 여부에 따라 달라서, 로그인 복원이 끝난 뒤 보는 사람을 키에 넣어 부른다.
export function usePostDetail(postId: number) {
  const { viewer, ready } = useViewer()
  return useQuery({
    queryKey: postKeys.detail(postId, viewer),
    enabled: ready && Number.isInteger(postId) && postId > 0,
    queryFn: () => fetchPost(postId),
    // 없는 글(404)은 다시 불러도 같으므로 재시도하지 않는다
    retry: false,
  })
}

// 요청이 끝나면 서버 응답으로 캐시를 바로 고치고, 다시 불러오기는 기다리지 않고 뒤에서 돌린다.
// 다시 불러오기를 기다리면 그동안 요청이 「진행 중」으로 남아 모달이 늦게 닫힌다 (테스트 서버는 왕복 약 0.5초).
// 실패(이미 처리됨, 정원 초과 등)해도 화면이 서버와 달라졌을 수 있어 다시 불러온다.
function usePostCache() {
  const queryClient = useQueryClient()
  return {
    refresh: () => {
      void queryClient.invalidateQueries({ queryKey: postKeys.all })
    },
    // 상세 키에는 보는 사람이 들어 있어서 그 글의 상세를 모두 고친다
    patchDetail: (postId: number, patch: (post: PostDetail) => Partial<PostDetail>) =>
      queryClient.setQueriesData<PostDetail>(
        { queryKey: [...postKeys.details(), postId] },
        (post) => (post ? { ...post, ...patch(post) } : post),
      ),
    patchApplicants: (
      postId: number,
      status: ApplicantStatus,
      patch: (list: ApplicantList) => ApplicantList,
    ) =>
      queryClient.setQueryData<ApplicantList>(postKeys.applicantTab(postId, status), (list) =>
        list ? patch(list) : list,
      ),
  }
}

// 대기 탭에서 처리한 지원자를 빼고, 탭 인원 수를 옮긴다 (REC-003 ②)
function movePending(list: ApplicantList, applicationId: number, to: 'accepted' | 'rejected') {
  if (!list.items.some((item) => item.applicationId === applicationId)) return list
  return {
    items: list.items.filter((item) => item.applicationId !== applicationId),
    counts: {
      ...list.counts,
      pending: Math.max(0, list.counts.pending - 1),
      [to]: list.counts[to] + 1,
    },
  }
}

// 다른 탭은 목록을 갖고 있지 않으니 인원 수만 옮긴다
function moveCount(list: ApplicantList, to: 'accepted' | 'rejected', amount = 1) {
  return {
    ...list,
    counts: {
      ...list.counts,
      pending: Math.max(0, list.counts.pending - amount),
      [to]: list.counts[to] + amount,
    },
  }
}

// REC-001 지원하기: 응답의 지원 ID로 「지원 완료」·「지원 취소」 상태로 바꾼다
export function useApplyToPost() {
  const cache = usePostCache()
  return useMutation({
    mutationFn: applyToPost,
    onSuccess: ({ applicationId }, postId) => {
      cache.patchDetail(postId, () => ({ myStatus: 'PENDING', myApplicationId: applicationId }))
    },
    onSettled: cache.refresh,
  })
}

// REC-002 지원 취소: 「지원하기」 상태로 돌아간다
export function useCancelApplication(postId: number) {
  const cache = usePostCache()
  return useMutation({
    mutationFn: cancelApplication,
    onSuccess: () => {
      cache.patchDetail(postId, () => ({ myStatus: 'NONE', myApplicationId: null }))
    },
    onSettled: cache.refresh,
  })
}

// PST-003 수정. 응답이 상세 전체라서 그 값으로 상세를 바꾸고 바로 상세로 돌아간다.
export function useUpdatePost() {
  const cache = usePostCache()
  return useMutation({
    mutationFn: updatePost,
    onSuccess: (updated, { postId }) => {
      cache.patchDetail(postId, () => updated)
    },
    onSettled: cache.refresh,
  })
}

// PST-007 공모전 사진 저장(삭제·올리기·순서). 글 작성·수정이 끝난 뒤 부르고, 끝나면 상세를 새로 받는다.
export function useSavePostImages() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: savePostImages,
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: postKeys.details() })
    },
  })
}

// PST-004 삭제. 목록으로 가므로 목록만 다시 불러온다 (지운 글의 상세를 다시 부르면 404가 난다).
export function useDeletePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deletePost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: postKeys.lists() })
    },
  })
}

// REC-003 지원자 목록 (작성자만). 탭마다 따로 불러오고, 탭 인원 수는 응답의 counts를 쓴다.
export function useApplicants(postId: number, status: ApplicantStatus, enabled = true) {
  return useQuery({
    queryKey: postKeys.applicantTab(postId, status),
    queryFn: () => fetchApplicants({ postId, status }),
    enabled,
  })
}

// REC-004 수락: 모집 인원·상태는 응답 값으로, 지원자는 대기 탭에서 수락 탭으로 옮긴다
export function useAcceptApplication(postId: number) {
  const cache = usePostCache()
  return useMutation({
    mutationFn: acceptApplication,
    onSuccess: ({ currentCount, targetCount, postStatus }, applicationId) => {
      cache.patchDetail(postId, () => ({ currentCount, targetCount, status: postStatus }))
      cache.patchApplicants(postId, 'PENDING', (list) =>
        movePending(list, applicationId, 'accepted'),
      )
      cache.patchApplicants(postId, 'ACCEPTED', (list) => moveCount(list, 'accepted'))
      cache.patchApplicants(postId, 'REJECTED', (list) => moveCount(list, 'accepted'))
    },
    onSettled: cache.refresh,
  })
}

// REC-005 거절: 지원자를 대기 탭에서 거절 탭으로 옮긴다. 모집 인원은 그대로다.
export function useRejectApplication(postId: number) {
  const cache = usePostCache()
  return useMutation({
    mutationFn: rejectApplication,
    onSuccess: (_, applicationId) => {
      cache.patchApplicants(postId, 'PENDING', (list) =>
        movePending(list, applicationId, 'rejected'),
      )
      cache.patchApplicants(postId, 'ACCEPTED', (list) => moveCount(list, 'rejected'))
      cache.patchApplicants(postId, 'REJECTED', (list) => moveCount(list, 'rejected'))
    },
    onSettled: cache.refresh,
  })
}

// REC-006 모집 완료: 상태를 모집완료로 바꾸고, 남은 대기 지원은 거절로 옮긴다 (④)
export function useCompletePost() {
  const cache = usePostCache()
  return useMutation({
    mutationFn: completePost,
    onSuccess: ({ autoRejectedCount }, postId) => {
      cache.patchDetail(postId, () => ({ status: 'COMPLETED' }))
      cache.patchApplicants(postId, 'PENDING', (list) => ({
        items: [],
        counts: { ...list.counts, pending: 0, rejected: list.counts.rejected + autoRejectedCount },
      }))
      cache.patchApplicants(postId, 'ACCEPTED', (list) =>
        moveCount(list, 'rejected', autoRejectedCount),
      )
      cache.patchApplicants(postId, 'REJECTED', (list) =>
        moveCount(list, 'rejected', autoRejectedCount),
      )
    },
    onSettled: cache.refresh,
  })
}
