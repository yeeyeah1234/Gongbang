import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchContests } from '../contest/api'
import { useViewer } from '../contest/hooks'
import {
  applyToPost,
  cancelApplication,
  createPost,
  fetchPost,
  fetchPosts,
  updatePost,
} from './api'

// PST-001 ⑦: 20개씩 페이지로 나눈다
export const POST_PAGE_SIZE = 20

export const postKeys = {
  all: ['posts'] as const,
  list: (page: number) => [...postKeys.all, 'list', page] as const,
  details: () => [...postKeys.all, 'detail'] as const,
  detail: (postId: number, viewer: string) => [...postKeys.details(), postId, viewer] as const,
}

// PST-001 모집글 목록. 페이지를 넘길 때 앞 페이지를 잠깐 그대로 보여 줘서 표가 깜빡이지 않게 한다.
export function usePostList(page: number) {
  return useQuery({
    queryKey: postKeys.list(page),
    queryFn: () => fetchPosts({ page, size: POST_PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
}

// PST-002 작성. 등록하면 목록을 다시 불러오게 한다.
export function useCreatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createPost,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: postKeys.all }),
  })
}

// PST-002 ②: 진행 중인 공모전을 제목으로 검색 (최규선 MAIN-01 API, status=active)
const CONTEST_SEARCH_SIZE = 8

export function useActiveContestSearch(keyword: string) {
  return useQuery({
    queryKey: ['contest-search', 'post-form', keyword],
    enabled: keyword.length > 0,
    queryFn: () =>
      fetchContests({
        status: 'active',
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

// REC-001·002: 지원하거나 취소하면 상세(버튼 상태)와 목록을 다시 불러온다
function useRefreshPosts() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: postKeys.all })
}

export function useApplyToPost() {
  const refresh = useRefreshPosts()
  return useMutation({ mutationFn: applyToPost, onSettled: refresh })
}

export function useCancelApplication() {
  const refresh = useRefreshPosts()
  return useMutation({ mutationFn: cancelApplication, onSettled: refresh })
}

// PST-003 수정. 저장하면 목록·상세를 다시 불러오게 한다.
export function useUpdatePost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updatePost,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: postKeys.all }),
  })
}
