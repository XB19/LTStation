import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Statistic, Table, Tree, Typography, message } from 'antd'
import { PlusOutlined, EditOutlined } from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import { get, post, put } from '../../api/http'
import PageHeader from '../../components/PageHeader'

interface TreeNode {
  id: number
  name: string
  parentId: number | null
  children: TreeNode[]
}

interface Totals {
  dayAmount: number
  dayVolume: number
  monthAmount: number
  monthVolume: number
  yearAmount: number
  yearVolume: number
  validSecretKey: string | null
}

interface ChildRow {
  key: string
  name: string
  totalAmount: number
  totalVolume: number
}

interface AntTreeNode {
  key: number
  title: string
  children: AntTreeNode[]
}

const toTreeData = (nodes: TreeNode[]): AntTreeNode[] =>
  nodes.map((n) => ({ key: n.id, title: n.name, children: toTreeData(n.children) }))

/** Gestion de la plateforme (Main/AccountManager d'origine). */
export default function AccountManager() {
  const [tree, setTree] = useState<TreeNode[]>([])
  const [selected, setSelected] = useState<{ id: number; name: string } | null>(null)
  const [totals, setTotals] = useState<Totals | null>(null)
  const [period, setPeriod] = useState(0)
  const [rows, setRows] = useState<ChildRow[]>([])
  const [modal, setModal] = useState<'add' | 'edit' | null>(null)
  const [form] = Form.useForm()

  const loadTree = useCallback(() => get<TreeNode[]>('/company-groups/tree').then(setTree), [])
  useEffect(() => {
    loadTree()
  }, [loadTree])

  useEffect(() => {
    if (!selected) return
    get<Totals>(`/company-groups/${selected.id}/totals`).then(setTotals)
  }, [selected])

  useEffect(() => {
    if (!selected) return
    get<ChildRow[]>(`/company-groups/${selected.id}/children-totals`, { period }).then(setRows)
  }, [selected, period])

  const openModal = (kind: 'add' | 'edit') => {
    if (!selected) {
      message.error('Veuillez sélectionner un nœud')
      return
    }
    form.setFieldsValue({ name: kind === 'edit' ? selected.name : '' })
    setModal(kind)
  }

  const save = async () => {
    const { name } = await form.validateFields()
    if (!selected) return
    if (modal === 'add') {
      await post(`/company-groups/${selected.id}/children`, { name })
    } else {
      await put(`/company-groups/${selected.id}`, { name })
      setSelected({ ...selected, name })
    }
    message.success('Opération réussie')
    setModal(null)
    loadTree()
  }

  return (
    <>
      <PageHeader title="Gestion de la plateforme" section="Paramètres de base" />
      <Row gutter={16}>
        <Col xs={24} lg={8}>
          <Card title="Comptes" style={{ minHeight: 500 }}>
            {tree.length > 0 && (
              <Tree
                defaultExpandAll
                treeData={toTreeData(tree)}
                onSelect={(keys, info) => {
                  if (keys.length) setSelected({ id: Number(keys[0]), name: String(info.node.title) })
                }}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={16}>
          <Card
            title={
              <Space>
                <span>Compte actuel : {selected?.name ?? '—'}</span>
              </Space>
            }
            extra={
              <Space>
                <Button icon={<PlusOutlined />} onClick={() => openModal('add')}>
                  Ajouter un sous-compte
                </Button>
                <Button icon={<EditOutlined />} onClick={() => openModal('edit')}>
                  Modifier
                </Button>
              </Space>
            }
          >
            {!selected || !totals ? (
              <Empty description="Sélectionnez un compte dans l'arbre" />
            ) : (
              <>
                <Typography.Title level={5}>Montant total</Typography.Title>
                <Row gutter={16}>
                  <Col span={8}><Statistic title="Jour" value={totals.dayAmount} precision={2} /></Col>
                  <Col span={8}><Statistic title="Mois" value={totals.monthAmount} precision={2} /></Col>
                  <Col span={8}><Statistic title="Année" value={totals.yearAmount} precision={2} /></Col>
                </Row>
                <Typography.Title level={5} style={{ marginTop: 16 }}>Volume total</Typography.Title>
                <Row gutter={16}>
                  <Col span={8}><Statistic title="Jour" value={totals.dayVolume} precision={2} /></Col>
                  <Col span={8}><Statistic title="Mois" value={totals.monthVolume} precision={2} /></Col>
                  <Col span={8}><Statistic title="Année" value={totals.yearVolume} precision={2} /></Col>
                </Row>
                <Typography.Paragraph style={{ marginTop: 16 }}>
                  N° d'application de la plateforme :{' '}
                  <Typography.Text type="danger" copyable={!!totals.validSecretKey}>
                    {totals.validSecretKey ?? '—'}
                  </Typography.Text>
                </Typography.Paragraph>
              </>
            )}
          </Card>
          <Card
            style={{ marginTop: 16 }}
            title="Informations des subordonnés"
            extra={
              <Select
                value={period}
                onChange={setPeriod}
                style={{ width: 160 }}
                options={[
                  { value: 0, label: 'Rapport du jour' },
                  { value: 1, label: 'Rapport du mois' },
                  { value: 2, label: "Rapport de l'année" },
                ]}
              />
            }
          >
            <Table
              size="small"
              dataSource={rows}
              pagination={{ pageSize: 8 }}
              columns={[
                { title: 'Subordonné', dataIndex: 'name' },
                { title: 'Montant total', dataIndex: 'totalAmount' },
                { title: 'Volume total', dataIndex: 'totalVolume' },
              ]}
            />
            {rows.some((r) => Number(r.totalAmount) > 0) && (
              <ReactECharts
                style={{ height: 400 }}
                option={{
                  title: { text: 'Rapport des données de la plateforme', left: 'center' },
                  tooltip: { trigger: 'item' },
                  legend: { top: '8%', left: 'center' },
                  series: [
                    {
                      name: 'Montant',
                      type: 'pie',
                      radius: ['0%', '60%'],
                      top: '15%',
                      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                      data: rows.map((r) => ({ name: r.name, value: r.totalAmount })),
                    },
                  ],
                }}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title={modal === 'add' ? 'Ajouter un sous-compte' : 'Modifier le compte'}
        open={modal !== null}
        onOk={save}
        onCancel={() => setModal(null)}
        okText="Enregistrer"
        cancelText="Annuler"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Nom du compte" rules={[{ required: true, message: 'Nom obligatoire' }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
