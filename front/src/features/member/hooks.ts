import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../auth/store'
import {
  deleteHistory,
  fetchBookmarks,
  fetchHistory,
  fetchMyComments,
  fetchMyPosts,
  fetchMyProfile,
  fetchPublicProfile,
  updateMyProfile,
  type HistoryKind,
} from './api'
import type { BookmarkSort, UpdateProfileRequest } from './types'

export const memberKeys = {
  me: ['members', 'me'] as const,
  history: (kind: HistoryKind) => ['members', 'me', kind] as const,
  publicProfile: (id: number) => ['members', id, 'profile'] as const,
  posts: (page: number) => ['members', 'me', 'posts', page] as const,
  comments: (page: number) => ['members', 'me', 'comments', page] as const,
  bookmarks: (sort: BookmarkSort) => ['members', 'me', 'bookmarks', sort] as const,
}

export function useMyProfile() {
  return useQuery({ queryKey: memberKeys.me, queryFn: fetchMyProfile })
}

export function useUpdateMyProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateProfileRequest) => updateMyProfile(body),
    onSuccess: (profile) => {
      queryClient.setQueryData(memberKeys.me, profile)
      // GNB 닉네임도 바꾼다
      const member = useAuthStore.getState().member
      if (member)
        useAuthStore.getState().setMember({
          ...member,
          nickname: profile.nickname,
          profileImageUrl: profile.profileImageUrl,
        })
    },
  })
}

export function useHistory<K extends HistoryKind>(kind: K) {
  return useQuery({ queryKey: memberKeys.history(kind), queryFn: () => fetchHistory(kind) })
}

export function useDeleteHistory(kind: HistoryKind) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteHistory(kind, id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: memberKeys.history(kind) })
      void queryClient.invalidateQueries({ queryKey: memberKeys.me })
    },
  })
}

export function usePublicProfile(memberId: number | null) {
  return useQuery({
    queryKey: memberKeys.publicProfile(memberId ?? 0),
    queryFn: () => fetchPublicProfile(memberId as number),
    enabled: memberId !== null,
  })
}

export function useMyPosts(page: number, enabled: boolean) {
  return useQuery({
    queryKey: memberKeys.posts(page),
    queryFn: () => fetchMyPosts(page),
    enabled,
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useMyComments(page: number, enabled: boolean) {
  return useQuery({
    queryKey: memberKeys.comments(page),
    queryFn: () => fetchMyComments(page),
    enabled,
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useBookmarks(sort: BookmarkSort) {
  return useQuery({ queryKey: memberKeys.bookmarks(sort), queryFn: () => fetchBookmarks(sort, 0) })
}
