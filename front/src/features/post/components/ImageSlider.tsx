import { useEffect, useState } from 'react'
import { DefaultThumbnail } from '../../contest/components/ContestCard'

// PST-007 ⑥·BRD-006 ⑤ 올린 사진 슬라이드: 좌우 버튼·키보드 화살표·아래 점으로 넘긴다. 첫 장이 대표
export function ImageSlider({
  images,
  label,
  altPrefix,
  aspectClass = 'aspect-4/3',
}: {
  images: { imageId: number; url: string }[]
  label: string // 영역 이름 (「공모전 사진」, 「결과물 사진」)
  altPrefix: string // 사진 대체 글 앞부분
  aspectClass?: string // 칸 비율 (모집글 오른쪽 칸은 공모전 포스터와 같은 aspect-3/4)
}) {
  const [index, setIndex] = useState(0)
  const [failed, setFailed] = useState<Set<number>>(() => new Set())
  // 처음 보이는 대표 사진(첫 장)을 다 받았거나 실패했는지. 그 뒤에 나머지 사진을 미리 받는다
  const [firstSettled, setFirstSettled] = useState(false)
  const count = images.length
  const restUrls = images
    .slice(1)
    .map((image) => image.url)
    .join('\n')

  // 넘기기 전에 나머지 사진을 미리 받아 둔다. 대표 사진이 끝난 뒤, 브라우저가 한가할 때 한 장씩 낮은 우선순위로 받는다
  useEffect(() => {
    if (!firstSettled || !restUrls) return
    const urls = restUrls.split('\n')
    let cancelled = false
    const loadNext = (i: number) => {
      if (cancelled || i >= urls.length) return
      const image = new Image()
      image.fetchPriority = 'low'
      image.decoding = 'async'
      image.onload = image.onerror = () => loadNext(i + 1)
      image.src = urls[i]
    }
    // Safari에는 requestIdleCallback이 없어 잠깐 기다렸다가 시작한다
    // (타입 정의에는 늘 있는 것으로 되어 있어 없을 수도 있다고 적어 둔다)
    const requestIdle = (window as Partial<Window>).requestIdleCallback
    if (requestIdle) {
      // window에서 떼어 부르면 브라우저가 막으므로 window.로 부른다
      const handle = window.requestIdleCallback(() => loadNext(0))
      return () => {
        cancelled = true
        window.cancelIdleCallback(handle)
      }
    }
    const timer = window.setTimeout(() => loadNext(0), 300)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [firstSettled, restUrls])
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
      className={`relative ${aspectClass} overflow-hidden rounded-card border border-line bg-surface-sub focus-visible:outline-2 focus-visible:outline-primary`}
    >
      <div aria-live="polite" className="size-full">
        {failed.has(current.imageId) ? (
          <DefaultThumbnail />
        ) : (
          <img
            key={current.imageId}
            src={current.url}
            alt={`${altPrefix} ${index + 1} / ${count}`}
            onLoad={() => setFirstSettled(true)}
            onError={() => {
              setFailed((prev) => new Set(prev).add(current.imageId))
              setFirstSettled(true)
            }}
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
