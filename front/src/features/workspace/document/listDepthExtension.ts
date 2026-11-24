import { Extension } from '@tiptap/core'
import type { Node as ProseMirrorNode, ResolvedPos } from '@tiptap/pm/model'

const LIST_WRAPPER_TYPES = new Set(['bulletList', 'orderedList', 'taskList'])
const LIST_ITEM_TYPES = new Set(['listItem', 'taskItem'])
const MAX_LIST_DEPTH = 3

function getCurrentListDepth($pos: ResolvedPos): number {
  let depth = 0
  for (let d = $pos.depth; d > 0; d -= 1) {
    if (LIST_WRAPPER_TYPES.has($pos.node(d).type.name)) {
      depth += 1
    }
  }
  return depth
}

function findCurrentListItemNode($pos: ResolvedPos): ProseMirrorNode | null {
  for (let d = $pos.depth; d > 0; d -= 1) {
    const node = $pos.node(d)
    if (LIST_ITEM_TYPES.has(node.type.name)) {
      return node
    }
  }
  return null
}

function getMaxNestedListDepth(node: ProseMirrorNode): number {
  let maxChild = 0
  node.forEach((child) => {
    const childDepth = getMaxNestedListDepth(child)
    const add = LIST_WRAPPER_TYPES.has(child.type.name) ? 1 : 0
    if (childDepth + add > maxChild) {
      maxChild = childDepth + add
    }
  })
  return maxChild
}

/**
 * DOC-06: 글머리·번호·체크리스트의 Tab 들여쓰기를 최대 3단계로 제한한다.
 * 기본 ListItem/TaskItem(priority 100)보다 높은 우선순위로 Tab을 검사해,
 * 현재 깊이(하위 중첩 목록 포함)가 3단계에 도달하면 들여쓰기를 막고 포커스 이탈도 방지한다.
 */
export const ListMaxDepth = Extension.create({
  name: 'listMaxDepth',
  priority: 200,

  addKeyboardShortcuts() {
    return {
      Tab: () => {
        const { selection } = this.editor.state
        const { $from } = selection
        const currentItem = findCurrentListItemNode($from)
        if (!currentItem) {
          return false
        }

        const currentDepth = getCurrentListDepth($from)
        const nestedDepth = getMaxNestedListDepth(currentItem)
        if (currentDepth + nestedDepth >= MAX_LIST_DEPTH) {
          // 3단계 이상이면 들여쓰기하지 않고 Tab 키를 소비한다
          return true
        }
        return false
      },
    }
  },
})
