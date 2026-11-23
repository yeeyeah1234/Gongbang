import { deletePostImage, reorderPostImages, uploadPostImage } from './api'
import type { PostImage } from './types'

// PST-007 ② 장당 10MB 이하, 최대 3장
export const POST_IMAGE_MAX = 3
export const POST_IMAGE_MAX_BYTES = 10 * 1024 * 1024

// 브라우저가 라이브러리 없이 띄우는 형식. 서버도 파일 앞부분 바이트로 다시 확인한다.
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/avif']
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'avif']
export const POST_IMAGE_ACCEPT = ALLOWED_TYPES.join(',')

// PST-007 ② 문구
export const POST_IMAGE_MESSAGES = {
  type: 'JPG·PNG·WebP·GIF·AVIF 사진만 올릴 수 있어요',
  size: '사진은 10MB 이하만 올릴 수 있어요',
  limit: '사진은 3장까지 올릴 수 있어요',
  failed: '사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요',
}

// 화면에서 다루는 사진 한 장: 이미 올린 사진이거나, 저장할 때 올릴 새 파일
export type PostImageDraft =
  | { key: string; kind: 'saved'; imageId: number; url: string }
  | { key: string; kind: 'new'; file: File; previewUrl: string }

export function toDrafts(images: PostImage[]): PostImageDraft[] {
  return images.map((image) => ({
    key: `saved-${image.imageId}`,
    kind: 'saved',
    imageId: image.imageId,
    url: image.url,
  }))
}

export function draftUrl(draft: PostImageDraft) {
  return draft.kind === 'saved' ? draft.url : draft.previewUrl
}

// 형식·크기가 맞지 않으면 그 사진만 빼고 안내 문구를 돌려준다 (②)
export function checkImageFile(file: File): string | null {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  const typeOk = file.type
    ? ALLOWED_TYPES.includes(file.type)
    : ALLOWED_EXTENSIONS.includes(extension)
  if (!typeOk) return POST_IMAGE_MESSAGES.type
  if (file.size > POST_IMAGE_MAX_BYTES) return POST_IMAGE_MESSAGES.size
  return null
}

let draftSeq = 0

export function newDraft(file: File): PostImageDraft {
  draftSeq += 1
  return { key: `new-${draftSeq}`, kind: 'new', file, previewUrl: URL.createObjectURL(file) }
}

export function releaseDraft(draft: PostImageDraft) {
  if (draft.kind === 'new') URL.revokeObjectURL(draft.previewUrl)
}

// 화면의 사진 목록(drafts)을 서버에 맞춘다: 뺀 사진 삭제 → 새 사진 차례로 올리기 → 순서가 다르면 순서 변경.
// 올리지 못한 사진은 건너뛰고 끝까지 진행한 뒤 실패한 장수를 돌려준다 (글은 이미 저장됐으므로 멈추지 않는다).
export async function savePostImages({
  postId,
  saved,
  drafts,
}: {
  postId: number
  saved: PostImage[]
  drafts: PostImageDraft[]
}) {
  let failed = 0
  const keptIds = new Set(drafts.flatMap((d) => (d.kind === 'saved' ? [d.imageId] : [])))
  for (const image of saved) {
    if (keptIds.has(image.imageId)) continue
    try {
      await deletePostImage({ postId, imageId: image.imageId })
    } catch {
      failed += 1
    }
  }

  // 새 사진은 서버가 맨 뒤에 붙이므로, 올린 결과의 마지막 사진 ID를 그 자리의 ID로 쓴다
  const finalIds: number[] = []
  let current: PostImage[] = saved.filter((image) => keptIds.has(image.imageId))
  for (const draft of drafts) {
    if (draft.kind === 'saved') {
      finalIds.push(draft.imageId)
      continue
    }
    try {
      const { images } = await uploadPostImage({ postId, file: draft.file })
      current = images
      finalIds.push(images[images.length - 1].imageId)
    } catch {
      failed += 1
    }
  }

  const currentIds = current.map((image) => image.imageId)
  const sameOrder =
    currentIds.length === finalIds.length && currentIds.every((id, i) => id === finalIds[i])
  if (!sameOrder && finalIds.length === currentIds.length && finalIds.length > 1) {
    try {
      await reorderPostImages({ postId, imageIds: finalIds })
    } catch {
      failed += 1
    }
  }
  return { failed }
}
