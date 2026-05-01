export type ApiRequestValue = {
  key: string
  valueSource: 'LITERAL' | 'PARAM'
  value?: string
  paramName?: string
  sampleValue?: string
}

export type ApiOutputField = {
  key: string
  label: string
  jsonPointer: string
  sortOrder?: number
  maskType?: string
  formatType?: string | null
  displayPipelineJson?: string | null
}

export type ApiQueryConfig = {
  presetKey?: string
  name?: string
  method: 'GET' | 'POST' | 'PUT'
  path: string
  responseRootPointer?: string
  bodyTemplate?: string
  localResultLimitParamName?: string
  localResultLimit?: number
  queryParams: ApiRequestValue[]
  headers: ApiRequestValue[]
  outputs: ApiOutputField[]
}

export type ApiPreset = {
  key: string
  datasourcePresetKey: string
  title: string
  summary: string
  commandHint: string
  sampleArgs: string[]
  config: ApiQueryConfig
}

/** 选 API 查询模板时回传给父表单：命令建议 + 默认查询名称（可改） */
export type ApiQueryPresetAppliedPayload = {
  commandHint: string
  queryDisplayName: string
}

export type ApiDatasourcePreset = {
  key: string
  title: string
  summary: string
  baseUrl: string
  healthcheckPath: string
  authType: 'NONE' | 'BEARER_TOKEN' | 'API_KEY_HEADER' | 'API_KEY_QUERY' | 'BASIC'
}

export const API_DATASOURCE_PRESETS: ApiDatasourcePreset[] = [
  {
    key: 'okx-p2p',
    title: '法币挂单价（OKX P2P）',
    summary: 'OKX C2C 挂单数据，适合做 USDT/CNY 场外买卖报价参考。',
    baseUrl: 'https://www.okx.com',
    healthcheckPath: '/v3/c2c/tradingOrders/books?quoteCurrency=CNY&baseCurrency=USDT&paymentMethod=all&side=sell&userType=all',
    authType: 'NONE',
  },
  {
    key: 'crypto-okx',
    title: '虚拟币价格（OKX）',
    summary: 'OKX 官方公开市场接口，适合做现货价格、盘口、主动买卖量等机器人。',
    baseUrl: 'https://www.okx.com',
    healthcheckPath: '/api/v5/market/ticker?instId=BTC-USDT',
    authType: 'NONE',
  },
  {
    key: 'weather-wttr',
    title: '天气查询',
    summary: '按城市获取当前天气，适合快速做“今天天气”机器人。',
    baseUrl: 'https://wttr.in',
    healthcheckPath: '/Beijing?format=j1',
    authType: 'NONE',
  },
  {
    key: 'ip-ipapi',
    title: 'IP 地理信息',
    summary: '根据 IP 查询国家、省市、运营商等信息。',
    baseUrl: 'https://ipapi.co',
    healthcheckPath: '/8.8.8.8/json/',
    authType: 'NONE',
  },
  {
    key: 'exchange-frankfurter',
    title: '汇率换算',
    summary: '按币种查询最新汇率，适合做币种换算机器人。',
    baseUrl: 'https://api.frankfurter.app',
    healthcheckPath: '/latest?from=USD&to=CNY',
    authType: 'NONE',
  },
  {
    key: 'holiday-nager',
    title: '节假日查询',
    summary: '按国家和年份查询公共假期列表。',
    baseUrl: 'https://date.nager.at',
    healthcheckPath: '/api/v3/PublicHolidays/2026/CN',
    authType: 'NONE',
  },
  {
    key: 'geocode-nominatim',
    title: '地理编码',
    summary: '地点名转经纬度，适合门店定位、地图查询场景。',
    baseUrl: 'https://nominatim.openstreetmap.org',
    healthcheckPath: '/search?q=Beijing&format=json&limit=1',
    authType: 'NONE',
  },
  {
    key: 'timezone-timeapi',
    title: '时区时间',
    summary: '按时区获取当地当前时间。',
    baseUrl: 'https://timeapi.io',
    healthcheckPath: '/api/Time/current/zone?timeZone=Asia/Shanghai',
    authType: 'NONE',
  },
  {
    key: 'crypto-coingecko',
    title: '虚拟币价格（推荐 CoinGecko）',
    summary: '查询主流币种价格、市值和涨跌数据（国内网络可用性通常优于 Binance，免费版限流）。',
    baseUrl: 'https://api.coingecko.com',
    healthcheckPath: '/api/v3/ping',
    authType: 'NONE',
  },
  {
    key: 'crypto-binance',
    title: '虚拟币价格（Binance，可能受限）',
    summary: '获取币安现货最新价格（部分地区可能访问受限，优先建议使用 CoinGecko 模板）。',
    baseUrl: 'https://api.binance.com',
    healthcheckPath: '/api/v3/ticker/price?symbol=BTCUSDT',
    authType: 'NONE',
  },
  {
    key: 'stock-twelvedata',
    title: '股票行情（TwelveData）',
    summary: '查询股票/指数行情，需 API Key（query 参数方式）。',
    baseUrl: 'https://api.twelvedata.com',
    healthcheckPath: '/time_series?symbol=AAPL&interval=1day&outputsize=1',
    authType: 'API_KEY_QUERY',
  },
  {
    key: 'sms-juhe',
    title: '手机号归属地（聚合）',
    summary: '手机号归属地查询，需 API Key（query 参数方式）。',
    baseUrl: 'http://apis.juhe.cn',
    healthcheckPath: '/mobile/get?phone=13800138000',
    authType: 'API_KEY_QUERY',
  },
  {
    key: 'weather-openweather',
    title: '天气查询（OpenWeather）',
    summary: '全球天气查询，需 API Key（query 参数方式）。',
    baseUrl: 'https://api.openweathermap.org',
    healthcheckPath: '/data/2.5/weather?q=Beijing&units=metric',
    authType: 'API_KEY_QUERY',
  },
]

export const API_QUERY_PRESETS: ApiPreset[] = [
  {
    key: 'weather-current',
    datasourcePresetKey: 'weather-wttr',
    title: '当前天气',
    summary: '输入城市名称，返回温度、体感、天气描述和湿度。',
    commandHint: 'weather',
    sampleArgs: ['Beijing'],
    config: {
      presetKey: 'weather-current',
      name: '当前天气',
      method: 'GET',
      path: '/{{city}}',
      responseRootPointer: '/current_condition/0',
      queryParams: [
        { key: 'format', valueSource: 'LITERAL', value: 'j1' },
        { key: 'lang', valueSource: 'LITERAL', value: 'zh' },
      ],
      headers: [],
      outputs: [
        { key: 'weatherDesc', label: '天气', jsonPointer: '/lang_zh/0/value', sortOrder: 0, maskType: 'NONE' },
        { key: 'temp_C', label: '温度', jsonPointer: '/temp_C', sortOrder: 1, maskType: 'NONE' },
        { key: 'FeelsLikeC', label: '体感温度', jsonPointer: '/FeelsLikeC', sortOrder: 2, maskType: 'NONE' },
        { key: 'humidity', label: '湿度', jsonPointer: '/humidity', sortOrder: 3, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'weather-forecast-today',
    datasourcePresetKey: 'weather-wttr',
    title: '今日天气预报',
    summary: '输入城市名称，返回今天最高/最低温、平均温度与日照时长。',
    commandHint: 'weather_today',
    sampleArgs: ['Beijing'],
    config: {
      presetKey: 'weather-forecast-today',
      name: '今日天气预报',
      method: 'GET',
      path: '/{{city}}',
      responseRootPointer: '/weather/0',
      queryParams: [
        { key: 'format', valueSource: 'LITERAL', value: 'j1' },
        { key: 'lang', valueSource: 'LITERAL', value: 'zh' },
      ],
      headers: [],
      outputs: [
        { key: 'date', label: '日期', jsonPointer: '/date', sortOrder: 0, maskType: 'NONE' },
        { key: 'maxtempC', label: '最高温度', jsonPointer: '/maxtempC', sortOrder: 1, maskType: 'NONE' },
        { key: 'mintempC', label: '最低温度', jsonPointer: '/mintempC', sortOrder: 2, maskType: 'NONE' },
        { key: 'avgtempC', label: '平均温度', jsonPointer: '/avgtempC', sortOrder: 3, maskType: 'NONE' },
        { key: 'sunHour', label: '日照时长', jsonPointer: '/sunHour', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'weather-forecast-tomorrow',
    datasourcePresetKey: 'weather-wttr',
    title: '明日天气预报',
    summary: '输入城市名称，返回明天最高/最低温、平均温度与 UV 指数。',
    commandHint: 'weather_tomorrow',
    sampleArgs: ['Beijing'],
    config: {
      presetKey: 'weather-forecast-tomorrow',
      name: '明日天气预报',
      method: 'GET',
      path: '/{{city}}',
      responseRootPointer: '/weather/1',
      queryParams: [
        { key: 'format', valueSource: 'LITERAL', value: 'j1' },
        { key: 'lang', valueSource: 'LITERAL', value: 'zh' },
      ],
      headers: [],
      outputs: [
        { key: 'date', label: '日期', jsonPointer: '/date', sortOrder: 0, maskType: 'NONE' },
        { key: 'maxtempC', label: '最高温度', jsonPointer: '/maxtempC', sortOrder: 1, maskType: 'NONE' },
        { key: 'mintempC', label: '最低温度', jsonPointer: '/mintempC', sortOrder: 2, maskType: 'NONE' },
        { key: 'avgtempC', label: '平均温度', jsonPointer: '/avgtempC', sortOrder: 3, maskType: 'NONE' },
        { key: 'uvIndex', label: 'UV 指数', jsonPointer: '/uvIndex', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'weather-sun-moon',
    datasourcePresetKey: 'weather-wttr',
    title: '日出日落',
    summary: '输入城市名称，返回日出日落与月相信息。',
    commandHint: 'sun',
    sampleArgs: ['Beijing'],
    config: {
      presetKey: 'weather-sun-moon',
      name: '日出日落',
      method: 'GET',
      path: '/{{city}}',
      responseRootPointer: '/weather/0/astronomy/0',
      queryParams: [
        { key: 'format', valueSource: 'LITERAL', value: 'j1' },
        { key: 'lang', valueSource: 'LITERAL', value: 'zh' },
      ],
      headers: [],
      outputs: [
        { key: 'sunrise', label: '日出', jsonPointer: '/sunrise', sortOrder: 0, maskType: 'NONE' },
        { key: 'sunset', label: '日落', jsonPointer: '/sunset', sortOrder: 1, maskType: 'NONE' },
        { key: 'moonrise', label: '月出', jsonPointer: '/moonrise', sortOrder: 2, maskType: 'NONE' },
        { key: 'moonset', label: '月落', jsonPointer: '/moonset', sortOrder: 3, maskType: 'NONE' },
        { key: 'moon_phase', label: '月相', jsonPointer: '/moon_phase', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'ip-lookup',
    datasourcePresetKey: 'ip-ipapi',
    title: 'IP 定位查询',
    summary: '输入 IP，返回国家、地区、城市、运营商和时区。',
    commandHint: 'ip',
    sampleArgs: ['8.8.8.8'],
    config: {
      presetKey: 'ip-lookup',
      name: 'IP 定位查询',
      method: 'GET',
      path: '/{{ip}}/json/',
      responseRootPointer: '',
      queryParams: [],
      headers: [],
      outputs: [
        { key: 'ip', label: 'IP', jsonPointer: '/ip', sortOrder: 0, maskType: 'NONE' },
        { key: 'country_name', label: '国家', jsonPointer: '/country_name', sortOrder: 1, maskType: 'NONE' },
        { key: 'region', label: '地区', jsonPointer: '/region', sortOrder: 2, maskType: 'NONE' },
        { key: 'city', label: '城市', jsonPointer: '/city', sortOrder: 3, maskType: 'NONE' },
        { key: 'org', label: '运营商', jsonPointer: '/org', sortOrder: 4, maskType: 'NONE' },
        { key: 'timezone', label: '时区', jsonPointer: '/timezone', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'fx-latest',
    datasourcePresetKey: 'exchange-frankfurter',
    title: '实时汇率',
    summary: '输入基准币和目标币，返回最新汇率与日期。',
    commandHint: 'fx',
    sampleArgs: ['USD CNY'],
    config: {
      presetKey: 'fx-latest',
      name: '实时汇率',
      method: 'GET',
      path: '/latest',
      responseRootPointer: '',
      queryParams: [
        { key: 'from', valueSource: 'PARAM', paramName: 'from', sampleValue: 'USD' },
        { key: 'to', valueSource: 'PARAM', paramName: 'to', sampleValue: 'CNY' },
      ],
      headers: [],
      outputs: [
        { key: 'date', label: '日期', jsonPointer: '/date', sortOrder: 0, maskType: 'NONE' },
        { key: 'base', label: '基准币', jsonPointer: '/base', sortOrder: 1, maskType: 'NONE' },
        { key: 'CNY', label: '目标币汇率', jsonPointer: '/rates/CNY', sortOrder: 2, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'holiday-country',
    datasourcePresetKey: 'holiday-nager',
    title: '国家节假日',
    summary: '输入年份和国家代码，返回节假日名称与日期。',
    commandHint: 'holiday',
    sampleArgs: ['2026 CN'],
    config: {
      presetKey: 'holiday-country',
      name: '国家节假日',
      method: 'GET',
      path: '/api/v3/PublicHolidays/{{year}}/{{countryCode}}',
      responseRootPointer: '/0',
      queryParams: [],
      headers: [],
      outputs: [
        { key: 'date', label: '日期', jsonPointer: '/date', sortOrder: 0, maskType: 'NONE' },
        { key: 'localName', label: '本地名称', jsonPointer: '/localName', sortOrder: 1, maskType: 'NONE' },
        { key: 'name', label: '英文名称', jsonPointer: '/name', sortOrder: 2, maskType: 'NONE' },
        { key: 'global', label: '全国假期', jsonPointer: '/global', sortOrder: 3, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'geo-forward',
    datasourcePresetKey: 'geocode-nominatim',
    title: '地点转经纬度',
    summary: '输入地点关键字，返回经纬度与展示名称。',
    commandHint: 'geo',
    sampleArgs: ['Tianjin'],
    config: {
      presetKey: 'geo-forward',
      name: '地点转经纬度',
      method: 'GET',
      path: '/search',
      responseRootPointer: '/0',
      queryParams: [
        { key: 'q', valueSource: 'PARAM', paramName: 'q', sampleValue: 'Tianjin' },
        { key: 'format', valueSource: 'LITERAL', value: 'jsonv2' },
        { key: 'limit', valueSource: 'LITERAL', value: '1' },
      ],
      headers: [],
      outputs: [
        { key: 'display_name', label: '地点', jsonPointer: '/display_name', sortOrder: 0, maskType: 'NONE' },
        { key: 'lat', label: '纬度', jsonPointer: '/lat', sortOrder: 1, maskType: 'NONE' },
        { key: 'lon', label: '经度', jsonPointer: '/lon', sortOrder: 2, maskType: 'NONE' },
        { key: 'type', label: '类型', jsonPointer: '/type', sortOrder: 3, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'time-zone-now',
    datasourcePresetKey: 'timezone-timeapi',
    title: '时区当前时间',
    summary: '输入 IANA 时区名，返回当地日期时间与星期。',
    commandHint: 'time',
    sampleArgs: ['Asia/Shanghai'],
    config: {
      presetKey: 'time-zone-now',
      name: '时区当前时间',
      method: 'GET',
      path: '/api/Time/current/zone',
      responseRootPointer: '',
      queryParams: [{ key: 'timeZone', valueSource: 'PARAM', paramName: 'timeZone', sampleValue: 'Asia/Shanghai' }],
      headers: [],
      outputs: [
        { key: 'timeZone', label: '时区', jsonPointer: '/timeZone', sortOrder: 0, maskType: 'NONE' },
        { key: 'dateTime', label: '当前时间', jsonPointer: '/dateTime', sortOrder: 1, maskType: 'NONE' },
        { key: 'date', label: '日期', jsonPointer: '/date', sortOrder: 2, maskType: 'NONE' },
        { key: 'time', label: '时间', jsonPointer: '/time', sortOrder: 3, maskType: 'NONE' },
        { key: 'dayOfWeek', label: '星期', jsonPointer: '/dayOfWeek', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-price',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'CoinGecko 币价',
    summary: '输入币种 ID，返回现价、市值、24h 涨跌与成交量。',
    commandHint: 'cg',
    sampleArgs: ['bitcoin'],
    config: {
      presetKey: 'coingecko-price',
      name: 'CoinGecko 币价',
      method: 'GET',
      path: '/api/v3/coins/markets',
      responseRootPointer: '/0',
      queryParams: [
        { key: 'vs_currency', valueSource: 'LITERAL', value: 'usd' },
        { key: 'ids', valueSource: 'PARAM', paramName: 'coinId', sampleValue: 'bitcoin' },
      ],
      headers: [],
      outputs: [
        { key: 'name', label: '币种', jsonPointer: '/name', sortOrder: 0, maskType: 'NONE' },
        { key: 'symbol', label: '代码', jsonPointer: '/symbol', sortOrder: 1, maskType: 'NONE' },
        { key: 'current_price', label: '现价(USD)', jsonPointer: '/current_price', sortOrder: 2, maskType: 'NONE' },
        { key: 'market_cap', label: '市值(USD)', jsonPointer: '/market_cap', sortOrder: 3, maskType: 'NONE' },
        { key: 'price_change_percentage_24h', label: '24h 涨跌幅(%)', jsonPointer: '/price_change_percentage_24h', sortOrder: 4, maskType: 'NONE' },
        { key: 'total_volume', label: '24h 成交量', jsonPointer: '/total_volume', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-top20',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'CoinGecko 市值榜汇总',
    summary: '返回按市值排序前 N 币的价格、涨跌幅和成交量概览（N 可传参）。',
    commandHint: 'cg_top',
    sampleArgs: ['20'],
    config: {
      presetKey: 'coingecko-top20',
      name: 'CoinGecko 市值榜汇总',
      method: 'GET',
      path: '/api/v3/coins/markets',
      responseRootPointer: '',
      queryParams: [
        { key: 'vs_currency', valueSource: 'LITERAL', value: 'usd' },
        { key: 'order', valueSource: 'LITERAL', value: 'market_cap_desc' },
        { key: 'per_page', valueSource: 'PARAM', paramName: 'limit', sampleValue: '20' },
        { key: 'page', valueSource: 'LITERAL', value: '1' },
        { key: 'sparkline', valueSource: 'LITERAL', value: 'false' },
      ],
      headers: [],
      outputs: [
        { key: 'market_cap_rank', label: '排名', jsonPointer: '/market_cap_rank', sortOrder: 0, maskType: 'NONE' },
        { key: 'name', label: '币种', jsonPointer: '/name', sortOrder: 1, maskType: 'NONE' },
        { key: 'symbol', label: '代码', jsonPointer: '/symbol', sortOrder: 2, maskType: 'NONE' },
        { key: 'current_price', label: '现价(USD)', jsonPointer: '/current_price', sortOrder: 3, maskType: 'NONE' },
        { key: 'price_change_percentage_24h', label: '24h 涨跌幅(%)', jsonPointer: '/price_change_percentage_24h', sortOrder: 4, maskType: 'NONE' },
        { key: 'total_volume', label: '24h 成交量', jsonPointer: '/total_volume', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-market-overview',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'CoinGecko 行情概览',
    summary: '输入币种 ID，返回市值、成交量、24h 涨跌与历史高低点。',
    commandHint: 'cg_market',
    sampleArgs: ['bitcoin'],
    config: {
      presetKey: 'coingecko-market-overview',
      name: 'CoinGecko 行情概览',
      method: 'GET',
      path: '/api/v3/coins/markets',
      responseRootPointer: '/0',
      queryParams: [
        { key: 'vs_currency', valueSource: 'LITERAL', value: 'usd' },
        { key: 'ids', valueSource: 'PARAM', paramName: 'coinId', sampleValue: 'bitcoin' },
      ],
      headers: [],
      outputs: [
        { key: 'name', label: '币种', jsonPointer: '/name', sortOrder: 0, maskType: 'NONE' },
        { key: 'symbol', label: '代码', jsonPointer: '/symbol', sortOrder: 1, maskType: 'NONE' },
        { key: 'current_price', label: '现价(USD)', jsonPointer: '/current_price', sortOrder: 2, maskType: 'NONE' },
        { key: 'market_cap', label: '市值(USD)', jsonPointer: '/market_cap', sortOrder: 3, maskType: 'NONE' },
        { key: 'total_volume', label: '24h 成交量', jsonPointer: '/total_volume', sortOrder: 4, maskType: 'NONE' },
        { key: 'price_change_percentage_24h', label: '24h 涨跌幅(%)', jsonPointer: '/price_change_percentage_24h', sortOrder: 5, maskType: 'NONE' },
        { key: 'ath', label: '历史最高(USD)', jsonPointer: '/ath', sortOrder: 6, maskType: 'NONE' },
        { key: 'atl', label: '历史最低(USD)', jsonPointer: '/atl', sortOrder: 7, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-trending',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'CoinGecko 热门币',
    summary: '返回当前热门币榜第一名的价格与市值排名。',
    commandHint: 'cg_hot',
    sampleArgs: [],
    config: {
      presetKey: 'coingecko-trending',
      name: 'CoinGecko 热门币',
      method: 'GET',
      path: '/api/v3/search/trending',
      responseRootPointer: '/coins/0/item',
      queryParams: [],
      headers: [],
      outputs: [
        { key: 'name', label: '币种', jsonPointer: '/name', sortOrder: 0, maskType: 'NONE' },
        { key: 'symbol', label: '代码', jsonPointer: '/symbol', sortOrder: 1, maskType: 'NONE' },
        { key: 'market_cap_rank', label: '市值排名', jsonPointer: '/market_cap_rank', sortOrder: 2, maskType: 'NONE' },
        { key: 'price_btc', label: '价格(BTC)', jsonPointer: '/price_btc', sortOrder: 3, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-global',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'CoinGecko 全局市场',
    summary: '返回全市场币种数量、总市值、总成交量与 BTC 市占率。',
    commandHint: 'cg_global',
    sampleArgs: [],
    config: {
      presetKey: 'coingecko-global',
      name: 'CoinGecko 全局市场',
      method: 'GET',
      path: '/api/v3/global',
      responseRootPointer: '/data',
      queryParams: [],
      headers: [],
      outputs: [
        { key: 'active_cryptocurrencies', label: '活跃币种数', jsonPointer: '/active_cryptocurrencies', sortOrder: 0, maskType: 'NONE' },
        { key: 'markets', label: '市场数量', jsonPointer: '/markets', sortOrder: 1, maskType: 'NONE' },
        { key: 'total_market_cap_usd', label: '总市值(USD)', jsonPointer: '/total_market_cap/usd', sortOrder: 2, maskType: 'NONE' },
        { key: 'total_volume_usd', label: '24h 总成交量(USD)', jsonPointer: '/total_volume/usd', sortOrder: 3, maskType: 'NONE' },
        { key: 'btc_dominance', label: 'BTC 市占率(%)', jsonPointer: '/market_cap_percentage/btc', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'coingecko-btc-eth',
    datasourcePresetKey: 'crypto-coingecko',
    title: 'BTC/ETH 双币价格',
    summary: '固定返回 BTC 与 ETH 的 USD/CNY 价格，适合日常播报。',
    commandHint: 'cg_pair',
    sampleArgs: [],
    config: {
      presetKey: 'coingecko-btc-eth',
      name: 'BTC/ETH 双币价格',
      method: 'GET',
      path: '/api/v3/simple/price',
      responseRootPointer: '',
      queryParams: [
        { key: 'ids', valueSource: 'LITERAL', value: 'bitcoin,ethereum' },
        { key: 'vs_currencies', valueSource: 'LITERAL', value: 'usd,cny' },
      ],
      headers: [],
      outputs: [
        { key: 'bitcoin_usd', label: 'BTC/USD', jsonPointer: '/bitcoin/usd', sortOrder: 0, maskType: 'NONE' },
        { key: 'bitcoin_cny', label: 'BTC/CNY', jsonPointer: '/bitcoin/cny', sortOrder: 1, maskType: 'NONE' },
        { key: 'ethereum_usd', label: 'ETH/USD', jsonPointer: '/ethereum/usd', sortOrder: 2, maskType: 'NONE' },
        { key: 'ethereum_cny', label: 'ETH/CNY', jsonPointer: '/ethereum/cny', sortOrder: 3, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-p2p-usdt-cny-best-sell',
    datasourcePresetKey: 'okx-p2p',
    title: 'OKX 买 U 列表（按条数）',
    summary: '参数表示返回条数，不是金额。系统会先抓更多候选，再稳定截取前 N 条。',
    commandHint: 'okx_p2p_buy',
    sampleArgs: ['10'],
    config: {
      presetKey: 'okx-p2p-usdt-cny-best-sell',
      name: 'OKX 买 U 列表（按条数）',
      method: 'GET',
      path: '/v3/c2c/tradingOrders/books',
      responseRootPointer: '/data/sell',
      localResultLimitParamName: 'limit',
      queryParams: [
        { key: 'quoteCurrency', valueSource: 'LITERAL', value: 'CNY' },
        { key: 'baseCurrency', valueSource: 'LITERAL', value: 'USDT' },
        { key: 'paymentMethod', valueSource: 'LITERAL', value: 'all' },
        { key: 'side', valueSource: 'LITERAL', value: 'sell' },
        { key: 'userType', valueSource: 'LITERAL', value: 'all' },
        { key: 'limit', valueSource: 'LITERAL', value: '50' },
      ],
      headers: [],
      outputs: [
        { key: 'price', label: '参考单价(CNY)', jsonPointer: '/price', sortOrder: 0, maskType: 'NONE' },
        { key: 'nickName', label: '商家', jsonPointer: '/nickName', sortOrder: 1, maskType: 'NONE' },
        { key: 'paymentMethod', label: '支付方式', jsonPointer: '/paymentMethods/0', sortOrder: 2, maskType: 'NONE' },
        { key: 'quoteMinAmountPerOrder', label: '最小金额', jsonPointer: '/quoteMinAmountPerOrder', sortOrder: 3, maskType: 'NONE' },
        { key: 'quoteMaxAmountPerOrder', label: '最大金额', jsonPointer: '/quoteMaxAmountPerOrder', sortOrder: 4, maskType: 'NONE' },
        { key: 'completedRate', label: '完成率', jsonPointer: '/completedRate', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-p2p-usdt-cny-best-buy',
    datasourcePresetKey: 'okx-p2p',
    title: 'OKX 卖 U 列表（按条数）',
    summary: '参数表示返回条数，不是金额。系统会先抓更多候选，再稳定截取前 N 条。',
    commandHint: 'okx_p2p_sell',
    sampleArgs: ['10'],
    config: {
      presetKey: 'okx-p2p-usdt-cny-best-buy',
      name: 'OKX 卖 U 列表（按条数）',
      method: 'GET',
      path: '/v3/c2c/tradingOrders/books',
      responseRootPointer: '/data/buy',
      localResultLimitParamName: 'limit',
      queryParams: [
        { key: 'quoteCurrency', valueSource: 'LITERAL', value: 'CNY' },
        { key: 'baseCurrency', valueSource: 'LITERAL', value: 'USDT' },
        { key: 'paymentMethod', valueSource: 'LITERAL', value: 'all' },
        { key: 'side', valueSource: 'LITERAL', value: 'buy' },
        { key: 'userType', valueSource: 'LITERAL', value: 'all' },
        { key: 'limit', valueSource: 'LITERAL', value: '50' },
      ],
      headers: [],
      outputs: [
        { key: 'price', label: '参考单价(CNY)', jsonPointer: '/price', sortOrder: 0, maskType: 'NONE' },
        { key: 'nickName', label: '商家', jsonPointer: '/nickName', sortOrder: 1, maskType: 'NONE' },
        { key: 'paymentMethod', label: '支付方式', jsonPointer: '/paymentMethods/0', sortOrder: 2, maskType: 'NONE' },
        { key: 'quoteMinAmountPerOrder', label: '最小金额', jsonPointer: '/quoteMinAmountPerOrder', sortOrder: 3, maskType: 'NONE' },
        { key: 'quoteMaxAmountPerOrder', label: '最大金额', jsonPointer: '/quoteMaxAmountPerOrder', sortOrder: 4, maskType: 'NONE' },
        { key: 'completedRate', label: '完成率', jsonPointer: '/completedRate', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-p2p-usdt-cny-featured-top3',
    datasourcePresetKey: 'okx-p2p',
    title: 'OKX 委托卡片（按条数）',
    summary: '复刻委托卡片区展示风格。参数表示要展示多少条卡片。',
    commandHint: 'okx_p2p_cards',
    sampleArgs: ['3'],
    config: {
      presetKey: 'okx-p2p-usdt-cny-featured-top3',
      name: 'OKX 委托卡片（按条数）',
      method: 'GET',
      path: '/v3/c2c/tradingOrders/books',
      responseRootPointer: '/data/sell',
      localResultLimitParamName: 'limit',
      queryParams: [
        { key: 'quoteCurrency', valueSource: 'LITERAL', value: 'CNY' },
        { key: 'baseCurrency', valueSource: 'LITERAL', value: 'USDT' },
        { key: 'paymentMethod', valueSource: 'LITERAL', value: 'all' },
        { key: 'side', valueSource: 'LITERAL', value: 'sell' },
        { key: 'userType', valueSource: 'LITERAL', value: 'all' },
        { key: 'limit', valueSource: 'LITERAL', value: '30' },
      ],
      headers: [],
      outputs: [
        { key: 'nickName', label: '商家', jsonPointer: '/nickName', sortOrder: 0, maskType: 'NONE' },
        { key: 'price', label: '价格(CNY)', jsonPointer: '/price', sortOrder: 1, maskType: 'NONE' },
        { key: 'availableAmount', label: '可卖数量(USDT)', jsonPointer: '/availableAmount', sortOrder: 2, maskType: 'NONE' },
        { key: 'quoteMinAmountPerOrder', label: '最小金额(CNY)', jsonPointer: '/quoteMinAmountPerOrder', sortOrder: 3, maskType: 'NONE' },
        { key: 'quoteMaxAmountPerOrder', label: '最大金额(CNY)', jsonPointer: '/quoteMaxAmountPerOrder', sortOrder: 4, maskType: 'NONE' },
        { key: 'completedRate', label: '完成率', jsonPointer: '/completedRate', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-p2p-usdt-cny-best-sell-top1',
    datasourcePresetKey: 'okx-p2p',
    title: 'OKX 买 U 最佳单条',
    summary: '只返回当前第一条可参考的买 U 挂单，适合做最佳报价机器人。',
    commandHint: 'okx_p2p_buy_best',
    sampleArgs: [],
    config: {
      presetKey: 'okx-p2p-usdt-cny-best-sell-top1',
      name: 'OKX 买 U 最佳单条',
      method: 'GET',
      path: '/v3/c2c/tradingOrders/books',
      responseRootPointer: '/data/sell',
      localResultLimit: 1,
      queryParams: [
        { key: 'quoteCurrency', valueSource: 'LITERAL', value: 'CNY' },
        { key: 'baseCurrency', valueSource: 'LITERAL', value: 'USDT' },
        { key: 'paymentMethod', valueSource: 'LITERAL', value: 'all' },
        { key: 'side', valueSource: 'LITERAL', value: 'sell' },
        { key: 'userType', valueSource: 'LITERAL', value: 'all' },
        { key: 'limit', valueSource: 'LITERAL', value: '20' },
      ],
      headers: [],
      outputs: [
        { key: 'price', label: '参考单价(CNY)', jsonPointer: '/price', sortOrder: 0, maskType: 'NONE' },
        { key: 'nickName', label: '商家', jsonPointer: '/nickName', sortOrder: 1, maskType: 'NONE' },
        { key: 'paymentMethod', label: '支付方式', jsonPointer: '/paymentMethods/0', sortOrder: 2, maskType: 'NONE' },
        { key: 'quoteMinAmountPerOrder', label: '最小金额', jsonPointer: '/quoteMinAmountPerOrder', sortOrder: 3, maskType: 'NONE' },
        { key: 'quoteMaxAmountPerOrder', label: '最大金额', jsonPointer: '/quoteMaxAmountPerOrder', sortOrder: 4, maskType: 'NONE' },
        { key: 'completedRate', label: '完成率', jsonPointer: '/completedRate', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-p2p-usdt-cny-best-buy-top1',
    datasourcePresetKey: 'okx-p2p',
    title: 'OKX 卖 U 最佳单条',
    summary: '只返回当前第一条可参考的卖 U 挂单，适合做最佳报价机器人。',
    commandHint: 'okx_p2p_sell_best',
    sampleArgs: [],
    config: {
      presetKey: 'okx-p2p-usdt-cny-best-buy-top1',
      name: 'OKX 卖 U 最佳单条',
      method: 'GET',
      path: '/v3/c2c/tradingOrders/books',
      responseRootPointer: '/data/buy',
      localResultLimit: 1,
      queryParams: [
        { key: 'quoteCurrency', valueSource: 'LITERAL', value: 'CNY' },
        { key: 'baseCurrency', valueSource: 'LITERAL', value: 'USDT' },
        { key: 'paymentMethod', valueSource: 'LITERAL', value: 'all' },
        { key: 'side', valueSource: 'LITERAL', value: 'buy' },
        { key: 'userType', valueSource: 'LITERAL', value: 'all' },
        { key: 'limit', valueSource: 'LITERAL', value: '20' },
      ],
      headers: [],
      outputs: [
        { key: 'price', label: '参考单价(CNY)', jsonPointer: '/price', sortOrder: 0, maskType: 'NONE' },
        { key: 'nickName', label: '商家', jsonPointer: '/nickName', sortOrder: 1, maskType: 'NONE' },
        { key: 'paymentMethod', label: '支付方式', jsonPointer: '/paymentMethods/0', sortOrder: 2, maskType: 'NONE' },
        { key: 'quoteMinAmountPerOrder', label: '最小金额', jsonPointer: '/quoteMinAmountPerOrder', sortOrder: 3, maskType: 'NONE' },
        { key: 'quoteMaxAmountPerOrder', label: '最大金额', jsonPointer: '/quoteMaxAmountPerOrder', sortOrder: 4, maskType: 'NONE' },
        { key: 'completedRate', label: '完成率', jsonPointer: '/completedRate', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-spot-ticker',
    datasourcePresetKey: 'crypto-okx',
    title: 'OKX 现货价格',
    summary: '输入交易对（如 BTC-USDT），返回现价、买一卖一和 24h 数据。',
    commandHint: 'okx_ticker',
    sampleArgs: ['BTC-USDT'],
    config: {
      presetKey: 'okx-spot-ticker',
      name: 'OKX 现货价格',
      method: 'GET',
      path: '/api/v5/market/ticker',
      responseRootPointer: '/data/0',
      queryParams: [{ key: 'instId', valueSource: 'PARAM', paramName: 'instId', sampleValue: 'BTC-USDT' }],
      headers: [],
      outputs: [
        { key: 'instId', label: '交易对', jsonPointer: '/instId', sortOrder: 0, maskType: 'NONE' },
        { key: 'last', label: '最新价', jsonPointer: '/last', sortOrder: 1, maskType: 'NONE' },
        { key: 'bidPx', label: '买一价', jsonPointer: '/bidPx', sortOrder: 2, maskType: 'NONE' },
        { key: 'askPx', label: '卖一价', jsonPointer: '/askPx', sortOrder: 3, maskType: 'NONE' },
        { key: 'vol24h', label: '24h 成交量(币)', jsonPointer: '/vol24h', sortOrder: 4, maskType: 'NONE' },
        { key: 'volCcy24h', label: '24h 成交额(计价币)', jsonPointer: '/volCcy24h', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-spot-orderbook-top1',
    datasourcePresetKey: 'crypto-okx',
    title: 'OKX 盘口买一卖一',
    summary: '输入交易对，返回盘口第一档买卖价与挂单量。',
    commandHint: 'okx_book',
    sampleArgs: ['BTC-USDT'],
    config: {
      presetKey: 'okx-spot-orderbook-top1',
      name: 'OKX 盘口买一卖一',
      method: 'GET',
      path: '/api/v5/market/books',
      responseRootPointer: '/data/0',
      queryParams: [
        { key: 'instId', valueSource: 'PARAM', paramName: 'instId', sampleValue: 'BTC-USDT' },
        { key: 'sz', valueSource: 'LITERAL', value: '1' },
      ],
      headers: [],
      outputs: [
        { key: 'ask_price_1', label: '卖一价', jsonPointer: '/asks/0/0', sortOrder: 0, maskType: 'NONE' },
        { key: 'ask_size_1', label: '卖一量', jsonPointer: '/asks/0/1', sortOrder: 1, maskType: 'NONE' },
        { key: 'bid_price_1', label: '买一价', jsonPointer: '/bids/0/0', sortOrder: 2, maskType: 'NONE' },
        { key: 'bid_size_1', label: '买一量', jsonPointer: '/bids/0/1', sortOrder: 3, maskType: 'NONE' },
        { key: 'ts', label: '时间戳', jsonPointer: '/ts', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'okx-contract-taker-volume',
    datasourcePresetKey: 'crypto-okx',
    title: 'OKX 合约主动买卖量',
    summary: '输入合约 ID（如 BTC-USDT-SWAP），返回最新一根主动买卖量统计。',
    commandHint: 'okx_taker',
    sampleArgs: ['BTC-USDT-SWAP'],
    config: {
      presetKey: 'okx-contract-taker-volume',
      name: 'OKX 合约主动买卖量',
      method: 'GET',
      path: '/api/v5/rubik/stat/taker-volume-contract',
      responseRootPointer: '/data/0',
      queryParams: [{ key: 'instId', valueSource: 'PARAM', paramName: 'instId', sampleValue: 'BTC-USDT-SWAP' }],
      headers: [],
      outputs: [
        { key: 'ts', label: '时间戳', jsonPointer: '/0', sortOrder: 0, maskType: 'NONE' },
        { key: 'sellVol', label: '主动卖出量', jsonPointer: '/1', sortOrder: 1, maskType: 'NONE' },
        { key: 'buyVol', label: '主动买入量', jsonPointer: '/2', sortOrder: 2, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'stock-daily',
    datasourcePresetKey: 'stock-twelvedata',
    title: '股票日线',
    summary: '输入股票代码，返回最近一个交易日 OHLC 和成交量（需 key）。',
    commandHint: 'stock',
    sampleArgs: ['AAPL'],
    config: {
      presetKey: 'stock-daily',
      name: '股票日线',
      method: 'GET',
      path: '/time_series',
      responseRootPointer: '/values/0',
      queryParams: [
        { key: 'symbol', valueSource: 'PARAM', paramName: 'symbol', sampleValue: 'AAPL' },
        { key: 'interval', valueSource: 'LITERAL', value: '1day' },
        { key: 'outputsize', valueSource: 'LITERAL', value: '1' },
      ],
      headers: [],
      outputs: [
        { key: 'datetime', label: '时间', jsonPointer: '/datetime', sortOrder: 0, maskType: 'NONE' },
        { key: 'open', label: '开盘', jsonPointer: '/open', sortOrder: 1, maskType: 'NONE' },
        { key: 'high', label: '最高', jsonPointer: '/high', sortOrder: 2, maskType: 'NONE' },
        { key: 'low', label: '最低', jsonPointer: '/low', sortOrder: 3, maskType: 'NONE' },
        { key: 'close', label: '收盘', jsonPointer: '/close', sortOrder: 4, maskType: 'NONE' },
        { key: 'volume', label: '成交量', jsonPointer: '/volume', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'mobile-location',
    datasourcePresetKey: 'sms-juhe',
    title: '手机号归属地',
    summary: '输入手机号，返回省份、城市、运营商（需 key）。',
    commandHint: 'mobile',
    sampleArgs: ['13800138000'],
    config: {
      presetKey: 'mobile-location',
      name: '手机号归属地',
      method: 'GET',
      path: '/mobile/get',
      responseRootPointer: '/result',
      queryParams: [{ key: 'phone', valueSource: 'PARAM', paramName: 'phone', sampleValue: '13800138000' }],
      headers: [],
      outputs: [
        { key: 'province', label: '省份', jsonPointer: '/province', sortOrder: 0, maskType: 'NONE' },
        { key: 'city', label: '城市', jsonPointer: '/city', sortOrder: 1, maskType: 'NONE' },
        { key: 'company', label: '运营商', jsonPointer: '/company', sortOrder: 2, maskType: 'NONE' },
        { key: 'zip', label: '邮编', jsonPointer: '/zip', sortOrder: 3, maskType: 'NONE' },
        { key: 'areacode', label: '区号', jsonPointer: '/areacode', sortOrder: 4, maskType: 'NONE' },
      ],
    },
  },
  {
    key: 'weather-openweather-current',
    datasourcePresetKey: 'weather-openweather',
    title: 'OpenWeather 当前天气',
    summary: '输入城市名称，返回天气、温度、体感、湿度和风速（需 key）。',
    commandHint: 'ow',
    sampleArgs: ['Tianjin'],
    config: {
      presetKey: 'weather-openweather-current',
      name: 'OpenWeather 当前天气',
      method: 'GET',
      path: '/data/2.5/weather',
      responseRootPointer: '',
      queryParams: [
        { key: 'q', valueSource: 'PARAM', paramName: 'city', sampleValue: 'Tianjin' },
        { key: 'units', valueSource: 'LITERAL', value: 'metric' },
        { key: 'lang', valueSource: 'LITERAL', value: 'zh_cn' },
      ],
      headers: [],
      outputs: [
        { key: 'name', label: '城市', jsonPointer: '/name', sortOrder: 0, maskType: 'NONE' },
        { key: 'weather', label: '天气', jsonPointer: '/weather/0/description', sortOrder: 1, maskType: 'NONE' },
        { key: 'temp', label: '温度', jsonPointer: '/main/temp', sortOrder: 2, maskType: 'NONE' },
        { key: 'feels_like', label: '体感温度', jsonPointer: '/main/feels_like', sortOrder: 3, maskType: 'NONE' },
        { key: 'humidity', label: '湿度', jsonPointer: '/main/humidity', sortOrder: 4, maskType: 'NONE' },
        { key: 'wind_speed', label: '风速', jsonPointer: '/wind/speed', sortOrder: 5, maskType: 'NONE' },
      ],
    },
  },
]

export function findDatasourcePreset(key: string | null | undefined): ApiDatasourcePreset | undefined {
  return API_DATASOURCE_PRESETS.find((item) => item.key === key)
}

export function listQueryPresetsForDatasource(datasourcePresetKey: string | null | undefined): ApiPreset[] {
  if (!datasourcePresetKey) return API_QUERY_PRESETS
  return API_QUERY_PRESETS.filter((item) => item.datasourcePresetKey === datasourcePresetKey)
}
