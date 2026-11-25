import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  InvalidDocumentNameError,
  parseDocumentName,
} from '../src/documentName.js';

describe('문서 이름 해석 (parseDocumentName)', () => {
  it('doc:{양의정수} 형식이면 type=doc과 숫자 id를 돌려준다', () => {
    assert.deepEqual(parseDocumentName('doc:1'), {
      type: 'doc',
      id: 1,
      raw: 'doc:1',
    });
    assert.deepEqual(parseDocumentName('doc:500'), {
      type: 'doc',
      id: 500,
      raw: 'doc:500',
    });
  });

  it('team:{양의정수} 형식이면 type=team과 숫자 id를 돌려준다', () => {
    assert.deepEqual(parseDocumentName('team:1'), {
      type: 'team',
      id: 1,
      raw: 'team:1',
    });
    assert.deepEqual(parseDocumentName('team:42'), {
      type: 'team',
      id: 42,
      raw: 'team:42',
    });
  });

  it('whiteboard:{양의정수} 형식이면 type=whiteboard와 숫자 id를 돌려준다', () => {
    assert.deepEqual(parseDocumentName('whiteboard:1'), {
      type: 'whiteboard',
      id: 1,
      raw: 'whiteboard:1',
    });
    assert.deepEqual(parseDocumentName('whiteboard:99'), {
      type: 'whiteboard',
      id: 99,
      raw: 'whiteboard:99',
    });
  });

  it('잘못된 형식이거나 지원하지 않는 접두사는 거절한다', () => {
    const invalidNames = [
      '',
      'doc:',
      'doc:0',
      'doc:-1',
      'doc:01',
      'doc:abc',
      'doc:1:2',
      'doc: 1',
      'DOC:1',
      'team:',
      'team:0',
      'team:-1',
      'team:01',
      'TEAM:3',
      'whiteboard:',
      'whiteboard:0',
      'whiteboard:-1',
      'whiteboard:01',
      'WHITEBOARD:5',
      'board:10',
      'unknown:5',
      'doc:999999999999999999999',
    ];

    for (const name of invalidNames) {
      assert.throws(
        () => parseDocumentName(name),
        InvalidDocumentNameError,
        `거절되어야 하는 문서 이름: "${name}"`,
      );
    }
  });
});
