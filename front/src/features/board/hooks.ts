import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useViewer } from '../contest/hooks'
import { savePostImages } from '../post/postImages'
import type { ImageApi } from '../post/postImages'
import {
  createBoardPost,
  deleteBoardImage,
  deleteBoardPost,
  fetchBoardPost,
  fetchBoardPosts,
  fetchPopularBoardPosts,
  likeBoardPost,
  reorderBoardImages,
  unlikeBoardPost,
  updateBoardPost,
  uploadBoardImage,
} from './api'
import type { BoardCategory, BoardDetail, BoardSort } from './types'

// BRD-001 ②: 20개씩 (PST-001과 같은 제안값)
export const BOARD_PAGE_SIZE = 20

export const boardKeys = {
  all: ['board-posts'] as const,
  lists: () => [...boardKeys.all, 'list'] as const,
  list: (category: BoardCategory | null, sort: BoardSort, page: number) =>
    [...boardKeys.lists(), category, sort, page] as const,
  popular: (category: BoardCategory | null) => [...boardKeys.lists(), 'popular', category] as const,
  details: () => [...boardKeys.all, 'detail'] as const,
  detail: (boardPostId: number, viewer: string) =>
    [...boardKeys.details(), boardPostId, viewer] as const,
}

export function useBoardList(category: BoardCategory | null, sort: BoardSort, page: number) {
  return useQuery({
    queryKey: boardKeys.list(category, sort, page),
    queryFn: () => fetchBoardPosts({ category, sort, page, size: BOARD_PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
}

// BRD-008 인기글
export function usePopularBoardPosts(category: BoardCategory | null) {
  return useQuery({
    queryKey: boardKeys.popular(category),
    queryFn: () => fetchPopularBoardPosts(category),
  })
}

// BRD-004 상세. 로그인 여부에 따라 isLiked·isAuthor가 달라서 보는 사람별로 캐시를 나눈다
export function useBoardDetail(boardPostId: number) {
  const { viewer, ready } = useViewer()
  return useQuery({
    queryKey: boardKeys.detail(boardPostId, viewer),
    enabled: ready && Number.isInteger(boardPostId) && boardPostId > 0,
    queryFn: () => fetchBoardPost(boardPostId),
    // 없는 글(404)은 다시 불러도 같으므로 재시도하지 않는다
    retry: false,
  })
}

// 다시 불러오기는 기다리지 않는다 (테스트 서버 왕복이 길어 화면 이동이 늦어진다)
export function useCreateBoardPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createBoardPost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: boardKeys.lists() })
    },
  })
}

export function useUpdateBoardPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateBoardPost,
    onSuccess: (detail) => {
      // 받은 상세로 캐시를 바로 고친다
      queryClient.setQueriesData<BoardDetail>(
        { queryKey: [...boardKeys.details(), detail.boardPostId] },
        detail,
      )
      void queryClient.invalidateQueries({ queryKey: boardKeys.lists() })
    },
  })
}

// 삭제하면 목록으로 가므로 목록만 다시 불러온다 (지운 글의 상세를 다시 부르면 404)
export function useDeleteBoardPost() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteBoardPost,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: boardKeys.lists() })
    },
  })
}

// BRD-006 결과물 사진을 화면 목록에 맞춘다 (모집글 사진과 같은 순서로 처리)
const BOARD_IMAGE_API: ImageApi = {
  upload: uploadBoardImage,
  remove: deleteBoardImage,
  reorder: reorderBoardImages,
}

export function useSaveBoardImages() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (args: Omit<Parameters<typeof savePostImages>[0], 'api'>) =>
      savePostImages({ ...args, api: BOARD_IMAGE_API }),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: boardKeys.details() })
    },
  })
}

// BRD-007 ④ 좋아요: 응답을 기다리지 않고 바로 바꾸고, 실패하면 되돌린다
export function useToggleBoardLike(boardPostId: number) {
  const queryClient = useQueryClient()
  const detailKey = [...boardKeys.details(), boardPostId]

  return useMutation({
    mutationFn: (like: boolean) =>
      like ? likeBoardPost(boardPostId) : unlikeBoardPost(boardPostId),
    onMutate: async (like: boolean) => {
      await queryClient.cancelQueries({ queryKey: detailKey })
      const previous = queryClient.getQueriesData<BoardDetail>({ queryKey: detailKey })
      queryClient.setQueriesData<BoardDetail>({ queryKey: detailKey }, (old) =>
        old && old.isLiked !== like
          ? { ...old, isLiked: like, likeCount: old.likeCount + (like ? 1 : -1) }
          : old,
      )
      return { previous }
    },
    onError: (_error, _like, context) => {
      context?.previous.forEach(([key, data]) => queryClient.setQueryData(key, data))
    },
    onSuccess: ({ likeCount, isLiked }) => {
      // 다른 사람이 함께 누른 수까지 서버 값으로 맞춘다
      queryClient.setQueriesData<BoardDetail>({ queryKey: detailKey }, (old) =>
        old ? { ...old, likeCount, isLiked } : old,
      )
      void queryClient.invalidateQueries({ queryKey: boardKeys.lists() })
    },
  })
}
