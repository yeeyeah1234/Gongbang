import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchContests } from '../contest/api'
import { createPost, fetchPosts } from './api'

// PST-001 ⑦: 20개씩 페이지로 나눈다
export const POST_PAGE_SIZE = 20

export const postKeys = {
  all: ['posts'] as const,
  list: (page: number) => [...postKeys.all, 'list', page] as const,
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
