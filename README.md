# 宿舍报修系统 AI Coding 上机考核 · 题目文档

| 项 | 值 |
|---|---|
| 考核名称 | 宿舍报修系统 AI Coding 上机测试 |
| 总时长 | 90 分钟（从拿到仓库地址开始计时，归零收卷） |
| 考核对象 | 后端 / 全栈方向 |
| 考核基座 | [Yanglx-sky/dorm-repair-system-exam](https://github.com/Yanglx-sky/dorm-repair-system-exam)（宿舍报修管理系统，Spring Boot） |
| AI 政策 | 允许使用任意 AI 助手（ChatGPT / Claude / Copilot 等）、查官方文档、搜索引擎、自己写测试；**不要求记录使用过程**，评分只依据最终代码与测试结果 |
| 提交方式 | 把完整代码推送到**你自己新建的 GitHub 公开仓库**，并填写仓库根目录的 `提交说明.md`，把仓库地址提交给监考 |
| 面试追问 | 考生须能逐行解释每一处改动：为什么改、怎么验证的 |

## 一、考试说明

你将在 90 分钟内：把一个已有项目跑起来 → 读懂代码 → 修复两个真实缺陷 → 补齐一个功能接口 → 完成一个状态机改造（压轴）→ 提交到公开仓库。

**本项目刻意保留了若干问题与未完成的功能**，这不是一个"写完的项目"。四道必做题按顺序做，后一题难度更高；选做加分题时间不够可放弃。

评分构成：T1 缺陷修复 20% + T2 缺陷修复 20% + T3 新功能 20% + T4 状态机改造 30% + 选做代码评审 5% + 提交与回归 5%。存在一票否决项（见第六节）。

**做不完是正常的**，请优先保证已完成的部分是"对且说得清"的。

## 二、环境与约束

### 环境准备（开考前完成）

```bash
git clone https://github.com/Yanglx-sky/dorm-repair-system-exam.git
cd dorm-repair-system-exam
mvn spring-boot:run
```

看到 `Started DormRepairSystemApplication` 即启动成功，端口 **8082**。先把项目跑起来，**再动手改代码**。

| 依赖 | 要求 | 检查 |
|---|---|---|
| JDK | 17 或更高 | `java -version` |
| Maven | 3.6 或更高 | `mvn -v` |

不需要 MySQL / Redis / Node.js，不需要任何云服务账号或环境变量。

### 可用入口

| 入口 | 地址 | 说明 |
|---|---|---|
| 接口文档（重点） | http://localhost:8082/doc.html | Knife4j，测试期间主要靠它调接口 |
| 登录页 | http://localhost:8082/html/login.html | 页面版前端，可点着看现象 |
| 数据库控制台 | http://localhost:8082/h2-console | JDBC URL `jdbc:h2:mem:dormrepair`、用户 `sa`、密码留空（**默认值 `jdbc:h2:~/test` 必须手改**） |

### 调接口四步（Knife4j）

1. 打开 doc.html → **用户管理 → POST /users/login**，请求体 `{"account":"3001","password":"123456"}`
2. 复制返回里的 `accessToken`（注意：在响应**顶层**，不在 `data` 里）
3. 点页面右上角 **Authorize**，粘贴 token
4. 之后所有接口都能直接调试

### 内置账号与种子数据

| 账号 | 密码 | 角色 | 用户ID |
|---|---|---|---|
| `admin` | `admin123` | 管理员 | 1 |
| `3001` | `123456` | 学生 张三（1栋502） | 2 |
| `3002` | `123456` | 学生 李四（3栋301） | 3 |
| `2001` | `123456` | 维修人员 王师傅 | 4 |
| `2002` | `123456` | 维修人员 赵师傅 | 5 |

种子工单（内存库，每次重启恢复初始状态；新工单 ID 从 5 开始）：

| 工单 | 报修人 | 设备 | 状态 | 维修人员 |
|---|---|---|---|---|
| 1 | 张三(3001) | 水龙头 | 待处理 | 无 |
| 2 | 张三(3001) | 电灯 | 维修中 | 王师傅(2001) |
| 3 | 李四(3002) | 空调 | 已完成 | 王师傅(2001) |
| 4 | 李四(3002) | 马桶 | 已取消 | 无 |

### 允许 / 禁止（硬约束）

| 允许 | 禁止（一票否决） |
|---|---|
| 使用任意 AI 助手、写自己的测试、新增文件 | **不得删除或改写 `src/test` 下已有的任何测试**（含 `@Disabled` 等手段） |
| 在 `VALID_STATUSES` 中增加"待确认"等状态值 | 不得修改 `sql/schema.sql` / `sql/data.sql` 种子数据 |
| 按现有分层（Controller/Service）扩展 | 不得修改 `JwtInterceptor` / `SecurityConfig` 的权限配置走捷径 |
| — | 不得改动既有接口的路径、参数名、返回结构；不得改端口与 `file.storage.location` |

## 三、代码导览

### 两个约定（改代码时必须遵守）

1. **统一返回格式**：`{"success": true, "message": "...", "data": ...}`；分页接口 `data` 形如 `{"records": [...], "total": n, ...}`
2. **鉴权**：`Authorization: Bearer <accessToken>`；身份一律从令牌解析（`AuthUtil.currentUserId`），不信任请求体里的 userId / repairmanId；每个接口额外校验数据归属

### 文件职责

| 文件 | 职责 |
|---|---|
| `controller/RepairOrderController.java` | 工单全部接口：提交/查询/改状态/删除/接单/图片上下架 |
| `service/impl/RepairOrderServiceImpl.java` | 工单业务：查询构造、接单、删除连带清图 |
| `service/impl/OrderImageServiceImpl.java` | 图片记录的增删查 |
| `util/FileStorage.java` | 本地图片存取：`uploadImage` / `deleteImage`（删除内部吞异常只记日志） |
| `interceptor/JwtInterceptor.java` | 按「角色 + 路径模式」授权；无 token→401，角色不匹配→403 |
| `util/AuthUtil.java` | 当前登录用户上下文（userId / roleId） |
| `sql/data.sql` / `sql/schema.sql` | 种子数据 / 建表（`order_status` 为 `varchar(20)`，**无枚举约束**） |
| `src/test/java/` | 项目自带 65 个测试（回归网，不要删改） |

### 关键位置索引（行号以当前 HEAD 为准）

| 位置 | 内容 | 关联任务 |
|---|---|---|
| `RepairOrderController.java:38` | `VALID_STATUSES` 硬编码合法状态集合 | T4 |
| `RepairOrderController.java:114-116` | `listOrders` 查询分发（userId/repairmanId/status/分页） | T1 |
| `RepairOrderController.java:190-192` | `updateStatus` 状态流转与角色校验 | T4 |
| `RepairOrderController.java:436-438` | `deleteImage` 删除图片接口 | T2 |
| `RepairOrderController.java:481` | `canAccessOrder` 数据归属校验 | T2/T3 |
| `RepairOrderServiceImpl.java:84` | `getByRepairmanId` 维修人员维度查询 | T1 |
| `RepairOrderServiceImpl.java:114` | `deleteOrderWithImages`（**删文件 + 删记录的正确范式**） | T2 |
| `FileStorage.java:64` | `deleteImage(String imageUrl)` | T2 |
| `JwtInterceptor.java:64` | admin 权限 `(任意方法, "/repair-orders/**")` | T3 |
| `data.sql:34-55` | 4 条种子工单与自增序列复位 | 全部 |

## 四、T1 缺陷修复：维修人员能看到别人的工单（20%，建议 15 分钟）

**现象**：用维修人员 `2001` 登录，调 `GET /repair-orders?repairmanId=4`，列表里出现了**不是他接取**的工单——包括还没人接的、以及别人接的。

**复现步骤**

1. 用 `2001` 登录拿 token，Knife4j 里调 `GET /repair-orders`，参数 `repairmanId=4`
2. 观察返回：`data` 里出现了全部 4 条工单（实际只应有 2 条属于他）

**验收要求**

- 用 `2001` 的 token 调 `GET /repair-orders?repairmanId=4`，返回的**每一条**工单 `repairmanId` 都必须是 `4`（即恰好工单 2、3 两条，按 `updateTime` 倒序）
- 带 `status=已完成` 再查，只返回工单 3
- 用管理员 token 查 `repairmanId=999999`（不存在的维修人员），返回**空列表** `[]`
- 维修人员的其他功能（接单、标记完工）不受影响；学生查询自己的单、管理员分页查询均不受影响

**提示**：接口的参数确实接收到了，但查询结果没有被它过滤。

**不要动**：控制器层已经有"维修人员只能查自己"的权限校验（`listOrders` 开头），问题在服务层；不要通过改控制器"绕过"这个 bug。

## 五、T2 缺陷修复：删除订单图片后，图片文件没有被真正删除（20%，建议 20 分钟）

**现象**：`DELETE /repair-orders/images/{imageId}` 返回"删除成功"、数据库记录也没了，但图片的访问地址仍然能打开——文件还留在服务器上。

**复现步骤**

1. 用学生 `3001` 给自己的工单 1 上传图片：`POST /repair-orders/1/images`，`multipart/form-data`，参数名 `file`
2. 记下返回的 `id` 和 `imageUrl`，浏览器直接打开 `imageUrl`（形如 `http://localhost:8082/uploads/repair-orders/xxx.png`），确认能显示
3. 调 `DELETE /repair-orders/images/{id}`，返回"删除成功"
4. 再次打开第 2 步的地址——**图片仍然能打开**；`uploads/` 下的文件也还在

**验收要求**

- 删除接口调用成功后，该图片的访问地址应当访问不到（HTTP 404）
- 项目根目录 `uploads/` 下对应的文件应当消失
- 数据库里的图片记录同样要被删除（这一点现在是正常的，别改坏）
- 边界行为保持不变：图片不存在时仍然返回 400（不是 500 或 200）；删除他人工单的图片仍然 403
- "删除订单"连带删图的既有行为不受影响

**一点提醒**：修好后请自己再走一遍第 2～4 步确认文件真的没了——接口返回"删除成功"不等于文件被删掉。项目里已有正确的删文件范式可参考（`RepairOrderServiceImpl.deleteOrderWithImages`）。

## 六、T3 新功能：按设备类型统计接口（20%，建议 15 分钟）

管理员需要知道各类设备分别报修了多少次。请新增接口：

| 项 | 要求 |
|---|---|
| 方法与路径 | `GET /repair-orders/stats/device-type` |
| 权限 | 仅管理员可访问；学生和维修人员访问应返回 403 |
| 返回格式 | 与项目其他接口一致：`{"success": true, "data": [...]}` |
| data 结构 | `[{"deviceType": "水龙头", "count": 2}, {"deviceType": "电灯", "count": 1}]` |
| 统计口径 | **全部工单，不按状态过滤（含已取消的工单也计入）** |
| 排序 | 按 `count` 从大到小（数量相同时顺序不限） |
| 空数据 | `data` 返回空数组 `[]`（不要返回 null） |

**其他要求**

- 遵循项目现有分层（Controller 负责接口、Service 负责查询），不要把查询逻辑直接写在 Controller 里
- 沿用项目现有的日志风格与返回风格；不需要改动前端页面
- 新路径会天然落入现有拦截器 admin 的 `/repair-orders/**` 授权范围——**不要**为了"实现权限"去改权限配置

## 七、T4 压轴：把「完工确认」流程补齐（30%，建议 35 分钟）

**背景**：项目设计的流程是「维修人员完工 → 学生确认订单完成」，但现在维修人员可以**直接把工单置为「已完成」**，学生确认这一步形同虚设。

**需求**：新增一个「待确认」状态，状态流转改为：

| 当前状态 | 谁能操作 | 改成什么 |
|---|---|---|
| 待处理 | 维修人员（接单） | 维修中（已有，保持不变） |
| 维修中 | 该工单的维修人员 | **待确认**（提交完工） |
| 待确认 | **报修的学生本人** | 已完成（确认验收） |
| 待处理 / 维修中 | 报修的学生本人 | 已取消（已有，保持不变） |
| 任意 | 管理员 | 任意合法状态（不受限制） |

**具体要求**

- 维修人员**不能**再把工单直接置为「已完成」，只能置为「维修中」或「待确认」
- 只有「维修中」的工单可以被提交为「待确认」（不能把没修/已完工的单送去确认）
- 只有报修的学生**本人**能把「待确认」确认为「已完成」
- 学生**不能**跳过维修人员，把「维修中」直接确认成「已完成」
- 「待确认」的工单**不能**被学生取消
- 其他既有规则保持不变：待处理的单可以取消、非法状态值要被拒绝（400）、管理员不受限制

**几点说明**

- 数据库**不需要改**：`order_status` 是 `varchar(20)`，没有枚举约束（可翻 `sql/schema.sql` 确认）
- 前端页面可以不改：工单列表会把状态原样显示（「待确认」会正常显示），用接口文档验收即可
- 时间不够时，优先保证「维修人员不能直接置已完成」和「只有学生能确认」这两条正确

## 八、选做加分：代码评审（5%）

下面这段「学生取消报修」的实现有**多处**问题。请在 `提交说明.md` 里指出问题并说明修改方式（能直接改到项目里更好）。

> 注意：这是**评审用代码片段**，并不在项目当前代码中；请按"如果把它落到这个项目里"的角度评审。

```java
public boolean cancelOrder(Long orderId) {
    RepairOrder order = repairOrderService.getById(orderId);
    if (order.getOrderStatus() == "待处理") {
        order.setOrderStatus("已取消");
        return repairOrderService.updateById(order);
    }
    return false;
}
```

要求：**至少指出 2 处问题**，并说明每处该怎么改。说到点子上比说得多更重要。

## 九、如何自查验收

1. **每题都复现一遍**：不要只看代码就下结论——四道题里有三道的现象必须跑起来才能确认
2. **跑回归测试**：`mvn test`，**65 个测试应当全部通过**；出现失败说明你的改动影响了其他功能，先解决它（评测时也会在你的代码上跑一遍，不许删改测试）
3. **评测会补跑一组针对验收标准的自动化测试**：不要改动接口的路径、参数名、返回结构

## 十、提交方式

**必须把代码推送到你自己新建的公开仓库**，然后把仓库地址提交给监考。

1. GitHub 新建**空**仓库（**不要**勾选 Add README / .gitignore / license），仓库名例如 `dorm-repair-exam-你的名字`，可见性 **Public**
2. 在项目目录执行：
   ```bash
   git remote set-url origin https://github.com/<你的账号>/<你的仓库名>.git
   git add -A
   git commit -m "完成任务一~四"
   git push -u origin main
   ```
   （被拒时可用 `git push -u origin main --force`）
3. 打开仓库链接确认代码和 `提交说明.md` 可见，把地址提交给监考

### `提交说明.md`（仓库根目录已有模板）

每个完成的任务写四段：**现象 → 根本原因 → 改动 → 验证**。这是评价"你是否真的读懂了"的关键依据——用 AI 帮忙没关系，但你要能说清每一处改动的理由。

## 十一、时间分配与评分权重预览

| 阶段 | 时间 | 内容 |
|---|---|---|
| 0:00-0:15 | 15 min | T1 缺陷修复 |
| 0:15-0:35 | 20 min | T2 缺陷修复 |
| 0:35-0:50 | 15 min | T3 新功能 |
| 0:50-1:25 | 35 min | T4 状态机改造（有余力做选做） |
| 1:25-1:30 | 5 min | 复查与提交（提交前再跑一次 `mvn test`） |

| 权重项 | 分值 | 说明 |
|---|---|---|
| T1 缺陷修复 | 20 | 修复生效 12 / 边界查询 4 / 不影响其他功能 4 |
| T2 缺陷修复 | 20 | 文件真删除 10 / 数据库记录仍删 3 / 边界行为 3 / 自证复走 4 |
| T3 新功能 | 20 | 接口契约（路径/格式/排序/空数组）8 / 数据正确 6 / 权限 403 得 3 / 分层与风格 3 |
| T4 状态机改造 | 30 | 维修人员不能直置已完成 10 / 仅维修中可提交待确认 4 / 仅学生本人确认 8 / 待确认不可取消 3 / 管理员任意与非法状态拒绝 5 |
| 选做代码评审 | 5 | 说清 2 处得 3，每多 1 处 +1，上限 5 |
| 提交与回归 | 5 | 65 个既有测试全过 3 / 提交说明四段完整 2 |

**一票否决项**：删除或篡改 `src/test` 下测试；修改种子数据或权限配置让用例"通过"；改动既有接口契约；仓库不可公开访问导致无法评测。

评分细则由考官依据《02-验收标准.md》逐条判定；测试操作步骤见《03-测试用例.md》。**这两份考官文档不随本仓库分发，考生无需查找，也不必提交其中任何内容。**

## 十二、常见问题

| 问题 | 处理 |
|---|---|
| 8082 端口被占用 | 改 `src/main/resources/application.yml` 的 `server.port`，重启 |
| H2 控制台连不上 | 默认 JDBC URL 是 `jdbc:h2:~/test`，必须手改为 `jdbc:h2:mem:dormrepair`，用户 `sa`，密码留空 |
| 数据被我改乱了 | 内存库，重启项目即恢复初始数据；想改初始数据可改 `sql/data.sql`（但提交时不得依赖改种子数据通过考核） |
| 上传的图片存哪 | 项目根目录 `uploads/`，通过 `/uploads/**` 访问；数据库只存路径 |
| `mvn test` 报找不到依赖 | 确认网络可用；首次会下载较多依赖，耐心等待 |
| T4 做不完 | 正常。优先把 T1~T3 做扎实，并在 `提交说明.md` 写明 T4 做到哪一步、卡在哪里——说清思路也有分 |
