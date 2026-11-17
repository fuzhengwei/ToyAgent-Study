# Step16 · WorkflowAgent 设计图解

> 核心思想一句话：**图是数据（节点表 + 边表），执行器是通用 walker，流程说了算。**

| 本实现 | LangGraph 对应概念 |
|---|---|
| `nodes.put(name, fn)` | `graph.add_node(name, fn)` |
| `edges.put(from, to)` | `graph.add_edge(from, to)` |
| `conditionalEdges.put(from, router)` | `graph.add_conditional_edges(from, router)` |
| `chat()` 里的 while-walker | `graph.invoke(state)` 的图执行循环 |
| `MAX_HOPS` | `recursion_limit` |
| `State: Map<String,Object>` | `TypedDict / Pydantic State` |

## 一、总览：图的拓扑与一次典型流转

实线 = 固定边（拓扑直达），虚线 = 条件边（路由器读 State 决定）。

```mermaid
flowchart TD
    START([START<br/>用户输入写入 State.input]) --> classify

    subgraph nodes[节点表 Map&lt;String, NodeFn&gt;]
        classify["classify 路由节点<br/>choose_route 结构化选分支"]
        tool["tool 工具节点<br/>单轮 FC：get_weather / calculator"]
        faq["faq 问答节点<br/>纯生成，不带 tools"]
        polish["polish 润色节点<br/>汇合统一整理最终回答"]
    end

    classify -. "条件边 Router<br/>state.next ∈ {faq, tool}" .-> tool
    classify -.-> faq
    tool -->|"固定边"| polish
    faq -->|"固定边"| polish
    polish -->|"固定边"| END([END<br/>返回 State.answer])

    style START fill:#e8f5e9,stroke:#2e7d32
    style END fill:#ffebee,stroke:#c62828
    style tool fill:#e3f2fd,stroke:#1565c0
    style faq fill:#e3f2fd,stroke:#1565c0
```

走哪条路由模型在 classify 节点里经 `choose_route` 结构化决定：
问天气/算数 → `tool` 分支；普通闲聊 → `faq` 分支。但**能不能转圈、转到哪汇合，全是拓扑说了算**——节点函数自己不知道图长什么样。

## 二、设计全景：三个数据结构 + 一个通用执行器

LangGraph 的三个一等公民在这里全部落成 Map：

```mermaid
flowchart LR
    subgraph graph_data["图 = 数据（buildGraph 声明，只此一处）"]
        N["节点表<br/>Map&lt;String, NodeFn&gt;<br/>classify / tool / faq / polish"]
        E["固定边表<br/>Map&lt;String, String&gt;<br/>tool→polish, faq→polish, polish→END"]
        CE["条件边表<br/>Map&lt;String, Router&gt;<br/>classify → 读 state.next"]
    end

    subgraph runner["执行器 = 通用 walker（chat 方法，与业务无关）"]
        W{"当前节点 != END<br/>且 hop ≤ MAX_HOPS?"}
        R["执行当前节点函数<br/>（只读写 State）"]
        NX["找下一跳：<br/>条件边优先，固定边兜底"]
    end

    S([State<br/>input / next / observation / answer]) --- N
    N --- E --- CE
    graph_data --> W --> R --> NX
    NX -->|"下一跳"| W
    NX -->|"END / 熔断 / 未知节点"| OUT([返回 State.answer + trace])

    style graph_data fill:#f3e5f5,stroke:#6a1b9a
    style runner fill:#e8eaf6,stroke:#283593
```

想加节点、改分支、把图改成带环的 → 只改 `buildGraph()`，`chat()` 一行不动。
这就是"图是一等公民"：**拓扑是配置，不是代码。**

## 三、一次 tool 分支请求的完整时序

模型对接走标准 Function Calling（`choose_route` 路由 + `tools` 字段工具清单 + tool_calls 结构化回填）：

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant W as chat() 图执行器
    participant C as classify 节点
    participant M as ChatModel (LLM)
    participant T as tool 节点
    participant X as executeTool (本地 switch)
    participant P as polish 节点

    U->>W: chat("查一下杭州天气")
    W->>C: runNode(State)
    C->>M: chat(messages, ROUTER_TOOLS)
    M-->>C: tool_calls: choose_route{next:"tool", reason}
    C-->>W: State.next = "tool"
    W->>T: 条件边命中，执行 tool 节点
    T->>M: chat(messages, TOOLS)
    M-->>T: tool_calls: get_weather{city:"杭州"}
    T->>X: executeTool("get_weather", args)
    X-->>T: "晴，27℃"
    T-->>W: State.observation（单轮执行，不循环）
    W->>P: 固定边 tool --> polish
    P->>M: chat(messages)  ← 纯生成节点，不带 tools
    M-->>P: 润色后的最终回答
    P-->>W: State.answer
    W-->>U: "杭州晴，27℃"
```

注意 6–10：tool 节点只执行**一轮** Function Calling——节点内不允许自由转圈，
这是工作流与 ReAct 的本质区别：**ReAct 转不转模型说了算，工作流转不转拓扑说了算。**

## 四、成环能力与保险丝

LangGraph 允许图带环（比如"质量不达标 → 打回重做"），本实现同样支持——
条件边路由器返回已走过的节点名即可成环。正因为允许成环，执行器带了 `MAX_HOPS = 10` 保险丝：

```mermaid
flowchart LR
    A[节点A] --> B[节点B]
    B -->|"条件边：质量不达标"| A
    B -->|"条件边：达标"| END

    W["MAX_HOPS = 10<br/>超过即熔断"] -.->|盯着| A
```

这与 Step15 的 `MAX_STEPS` 是同一件事：**只要存在循环的可能，无论循环属于模型（ReAct）还是属于拓扑（工作流），运行时都必须有保险丝。**

## 五、与市面框架的对照

| 本实现 | LangGraph 对应概念 |
|---|---|
| `nodes.put(name, fn)` | `graph.add_node(name, fn)` |
| `edges.put(from, to)` | `graph.add_edge(from, to)` |
| `conditionalEdges.put(from, router)` | `graph.add_conditional_edges(from, router)` |
| `chat()` 里的 while-walker | `graph.invoke(state)` 的图执行循环 |
| `MAX_HOPS` | `recursion_limit` |
| `State: Map<String,Object>` | `TypedDict / Pydantic State` |

差异只有一条：LangGraph 的 State 有类型系统与通道合并（reducer）语义，教学版用 `Map` 直传——骨架思想完全同构。

## 六、官方资源

| 用途 | 地址 |
|---|---|
| 产品主页 | https://www.langchain.com/langgraph |
| 官方文档总览（Python） | https://docs.langchain.com/oss/python/langgraph/overview |
| **官方 Quickstart（最短对照案例，计算器 Agent）** | https://docs.langchain.com/oss/python/langgraph/quickstart |
| Graph API 概念 | https://docs.langchain.com/oss/python/langgraph/graph-api |
| GitHub 仓库 | https://github.com/langchain-ai/langgraph |

> 注意：langgraph.com.cn / langgraph.org.cn / github.langchain.ac.cn 等均为第三方中文镜像站，非官网。

**官方 Quickstart 与本实现的对应关系**（quickstart 的 `agent_builder` ≙ 本类的 `buildGraph()`）：

```python
agent_builder.add_node("llm_call", llm_call)      # ≙ nodes.put("classify", this::classifyNode) ...
agent_builder.add_node("tool_node", tool_node)
agent_builder.add_edge(START, "llm_call")          # ≙ start = "classify"
agent_builder.add_edge("tool_node", "llm_call")    # ≙ edges.put("tool", "polish") ...
agent_builder.add_conditional_edges(               # ≙ conditionalEdges.put("classify", state -> ...)
    "llm_call", should_continue, ["tool_node", END])
agent = agent_builder.compile()                    # ≙ chat() 里的通用 walker
agent.invoke({"messages": messages})               # ≙ chat(input)
```

条件边路由函数 `should_continue`（读 State 判断有无 tool_calls → 返回下一跳）与本类的 `Router` 接口逐字同构。课后拿这段 Python 对照本类 Java，10 分钟即可亲眼确认"同一个设计"。


## 七、执行轨迹

输入：`查一下北京天气，再算一下 12*8`，输出：`北京天气晴，26°；12×8=96。`

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant W as 图执行器 chat()
    participant C as classify 节点
    participant M as 模型 (LLM)
    participant T as tool 节点
    participant X as executeTool<br/>(本地 switch)
    participant P as polish 节点

    U->>W: "查一下北京天气，再算一下 12*8"
    W->>C: 执行节点 classify
    C->>M: chat(messages, ROUTER_TOOLS)
    M-->>C: tool_calls: choose_route{next:"tool", reason:"用户需要查询天气和进行数学计算，均需调用工具"}
    C-->>W: State.next = "tool"（真实模型给出的路由理由）
    W->>T: 条件边 classify --tool--> tool
    T->>M: chat(messages, TOOLS)
    M-->>T: 并发两条 tool_calls：get_weather{city:"北京"} ＋ calculator{expression:"12*8"}
    T->>X: executeTool("get_weather") → "晴，26°"
    T->>X: executeTool("calculator") → "96.0"
    T-->>W: State.observation = "晴，26°；96.0；"（单轮收齐，不循环）
    W->>P: 固定边 tool --> polish
    P->>M: chat(messages) 润色
    P-->>W: State.answer = "北京天气晴，26°；12×8=96。"
    W-->>U: END：classify → tool → polish → END
```

trace 事件与图上步骤的对应关系（8 条 trace 全部入图，步骤号 = `autonumber` 序号）：

| trace 事件 | 类型 | 图上步骤 |
|---|---|---|
| 路由决策 → tool（含模型给出的理由） | node | 4–5 |
| classify --tool--> tool 节点 | edge | 6 |
| get_weather {"city":"北京"} | tool | 8（并发发起）· 9（执行） |
| calculator {"expression":"12*8"} | tool | 8（并发发起）· 10（执行） |
| 晴，26°；96.0； | node | 11 |
| tool --> polish | edge | 12 |
| 最终回答已生成 | node | 13–14 |
| 执行路径: classify → tool → polish → END | edge | 15 |

**两个值得盯住的细节**：

1. **第 8 步是并发 tool_calls**——模型在一轮回复里同时发起了 get_weather 和 calculator 两个调用，执行器在第 9–10 步依次执行、分别按 tool_call_id 回填，这正是标准 Function Calling 协议的多工具能力；
2. **第 11 步单轮收齐不循环**——工具结果写入 State 后直接走固定边去 polish，节点内不回模型再问一轮：转不转圈，拓扑说了算（与 ReAct 的本质区别在真实轨迹里的样子）。
