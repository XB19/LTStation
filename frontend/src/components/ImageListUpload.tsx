import { Upload, type UploadFile } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { http } from '../api/http'

interface Props {
  value?: string[]
  onChange?: (v: string[]) => void
  max?: number
}

/** Liste d'images (URL /img/…) envoyées via /api/uploads/image, utilisable comme champ de formulaire. */
export default function ImageListUpload({ value = [], onChange, max = 10 }: Props) {
  const fileList: UploadFile[] = value.map((url, i) => ({ uid: `${i}-${url}`, name: url.split('/').pop() ?? url, status: 'done', url }))
  return (
    <Upload
      accept="image/*"
      listType="picture-card"
      fileList={fileList}
      onRemove={(f) => {
        onChange?.(value.filter((u) => u !== f.url))
        return true
      }}
      customRequest={async ({ file, onSuccess, onError }) => {
        try {
          const fd = new FormData()
          fd.append('file', file as Blob)
          const r = await http.post<{ url: string }>('/uploads/image', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
          onChange?.([...value, r.data.url])
          onSuccess?.(r.data)
        } catch (e) {
          onError?.(e as Error)
        }
      }}
    >
      {value.length < max && (
        <div>
          <PlusOutlined />
          <div style={{ marginTop: 8 }}>Ajouter</div>
        </div>
      )}
    </Upload>
  )
}
