import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchPosts } from './api'

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
