/**
 * 동기화 문서 이름 해석기.
 * 현재 지원: "doc:{documentId}", "team:{teamId}", "whiteboard:{whiteboardId}"
 */
export type ParsedDocumentName = {
  type: 'doc' | 'team' | 'whiteboard';
  id: number;
  raw: string;
};

export class InvalidDocumentNameError extends Error {
  readonly documentName: string;

  constructor(documentName: string) {
    super(`지원하지 않는 문서 이름 형식입니다: ${documentName}`);
    this.name = 'InvalidDocumentNameError';
    this.documentName = documentName;
  }
}

const DOCUMENT_NAME_PATTERN = /^([a-z]+):([1-9]\d*)$/;

export function parseDocumentName(name: string): ParsedDocumentName {
  const match = DOCUMENT_NAME_PATTERN.exec(name);
  if (!match) {
    throw new InvalidDocumentNameError(name);
  }

  const prefix = match[1];
  const rawId = match[2];
  if (!prefix || !rawId) {
    throw new InvalidDocumentNameError(name);
  }

  const id = Number(rawId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new InvalidDocumentNameError(name);
  }

  if (prefix === 'doc' || prefix === 'team' || prefix === 'whiteboard') {
    return { type: prefix, id, raw: name };
  }

  throw new InvalidDocumentNameError(name);
}
