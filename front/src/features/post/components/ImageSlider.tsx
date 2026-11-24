import { useState } from 'react'
import { DefaultThumbnail } from '../../contest/components/ContestCard'

// PST-007 ⑥·BRD-006 ⑤ 올린 사진 슬라이드: 좌우 버튼·키보드 화살표·아래 점으로 넘긴다. 첫 장이 대표
export function ImageSlider({
  images,
  label,
  altPrefix,
}: {
  images: { imageId: number; url: string }[]
  label: string // 영역 이름 (「공모전 사진」, 「결과물 사진」)
  altPrefix: string // 사진 대체 글 앞부분
}) {
  const [index, setIndex] = useState(0)
  const [failed, setFailed] = useState<Set<number>>(() => new Set())
  const count = images.length
  const current = images[Math.min(index, count - 1)]
  const go = (next: number) => setIndex((next + count) % count)

  const buttonClass =
    'absolute top-1/2 flex size-10 -translate-y-1/2 cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-text shadow-pop hover:bg-bg focus-visible:outline-2 focus-visible:outline-primary'

  return (
    <section
      aria-roledescription="carousel"
      aria-label={label}
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === 'ArrowLeft') go(index - 1)
        if (event.key === 'ArrowRight') go(index + 1)
      }}
      className="relative aspect-4/3 overflow-hidden rounded-card border border-line bg-surface-sub focus-visible:outline-2 focus-visible:outline-primary"
    >
      <div aria-live="polite" className="size-full">
        {failed.has(current.imageId) ? (
          <DefaultThumbnail />
        ) : (
          <img
            key={current.imageId}
            src={current.url}
            alt={`${altPrefix} ${index + 1} / ${count}`}
            onError={() => setFailed((prev) => new Set(prev).add(current.imageId))}
            className="size-full object-cover"
          />
        )}
      </div>
      <button
        type="button"
        aria-label="이전 사진"
        onClick={() => go(index - 1)}
        className={`${buttonClass} left-3`}
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="size-5" fill="none">
          <path d="M12 5 7 10l5 5" stroke="currentColor" strokeWidth="1.8" />
        </svg>
      </button>
      <button
        type="button"
        aria-label="다음 사진"
        onClick={() => go(index + 1)}
        className={`${buttonClass} right-3`}
      >
        <svg aria-hidden="true" viewBox="0 0 20 20" className="size-5" fill="none">
          <path d="m8 5 5 5-5 5" stroke="currentColor" strokeWidth="1.8" />
        </svg>
      </button>
      <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-2">
        {images.map((image, i) => (
          <button
            key={image.imageId}
            type="button"
            aria-label={`${i + 1}번째 사진 보기`}
            aria-current={i === index ? 'true' : undefined}
            onClick={() => setIndex(i)}
            className={[
              'size-2 cursor-pointer rounded-full',
              i === index ? 'bg-primary' : 'border border-line-strong bg-surface',
            ].join(' ')}
          />
        ))}
      </div>
    </section>
  )
}
