import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../auth/store'
import {
  deleteHistory,
  fetchBookmarks,
  fetchHistory,
  fetchMyComments,
  fetchMyApplications,
  fetchMyBoardPosts,
  fetchMyPosts,
  fetchMyProfile,
  fetchPublicProfile,
  updateMyProfile,
  uploadProfileImage,
  type HistoryKind,
} from './api'
import type { BookmarkSort, MyProfile, UpdateProfileRequest } from './types'

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

function useSyncProfile() {
  const queryClient = useQueryClient()
  return async (profile: MyProfile) => {
    await queryClient.cancelQueries({ queryKey: memberKeys.me, exact: true })
    queryClient.setQueryData(memberKeys.me, profile)
    const member = useAuthStore.getState().member
    if (member)
      useAuthStore.getState().setMember({
        ...member,
        nickname: profile.nickname,
        profileImageUrl: profile.profileImageUrl,
      })
    await queryClient.invalidateQueries({ queryKey: memberKeys.publicProfile(profile.memberId) })
  }
}

export function useUploadProfileImage() {
  const syncProfile = useSyncProfile()
  return useMutation({ mutationFn: uploadProfileImage, onSuccess: syncProfile })
}

export function useUpdateMyProfile() {
  const syncProfile = useSyncProfile()
  return useMutation({
    mutationFn: (body: UpdateProfileRequest) => updateMyProfile(body),
    onSuccess: syncProfile,
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

export function useMyApplications(page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['members', 'me', 'applications', page],
    queryFn: () => fetchMyApplications(page),
    enabled,
    retry: false,
  })
}
export function useMyBoardPosts(page: number, enabled: boolean) {
  return useQuery({
    queryKey: ['members', 'me', 'board-posts', page],
    queryFn: () => fetchMyBoardPosts(page),
    enabled,
    retry: false,
  })
}
