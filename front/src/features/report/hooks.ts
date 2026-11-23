import { useMutation } from '@tanstack/react-query'
import { createReport } from './api'
import type { CreateReportRequest } from './types'

export function useCreateReportMutation() {
  return useMutation({
    mutationFn: (payload: CreateReportRequest) => createReport(payload),
  })
}
