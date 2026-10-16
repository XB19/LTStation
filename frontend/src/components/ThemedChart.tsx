import type { ComponentProps } from 'react'
import ReactECharts from 'echarts-for-react'
import { useAppTheme } from '../theme/ThemeProvider'

/** Graphique ECharts qui suit le thème clair / sombre (fond transparent sur la carte). */
export default function ThemedChart(props: ComponentProps<typeof ReactECharts>) {
  const { mode } = useAppTheme()
  return (
    <ReactECharts
      {...props}
      theme={mode === 'dark' ? 'dark' : undefined}
      option={{ backgroundColor: 'transparent', ...props.option }}
    />
  )
}
