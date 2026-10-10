---
name: java-development-standards
description: 按用户提供的《开发规范skill.md》指导 netagent 的 Java 开发，落实薄 Controller、Service 只定义接口、Impl 承载业务、Mapper 负责 SQL 的强制分层，以及命名、格式、OOP、集合、并发和注释规范。适用于编写、修改或解释 Java 代码、接口及 POM；只做编译检查，不添加测试类或测试依赖，接口由用户使用 Apifox 的 IDEA 插件自行验证。
---

# Java 开发规范

## 规范正文

规范来源为用户提供的 `开发规范skill.md`，已完整导入本目录的 [开发规范正文](development-standards.md)。原始文件保持不变，不再使用已移除的 `alibaba-java-guidelines` Skill 作为规范入口，也不依赖在线手册才能读取规范。

在编写或修改 Java 代码前，读取正文中适用的章节以及相应说明、正例和反例，保持原文的【强制】【推荐】【参考】级别，不把反例当作正确实现。正文包含：

1. 命名风格。
2. 常量定义。
3. 代码格式。
4. OOP 规约。
5. 集合处理。
6. 并发处理。
7. 控制语句。
8. 注释规约。

正文按用户文件保留，不擅自补写未提供的章节或声称它是某个已核验的官方版本。遇到条款、示例相互矛盾或与当前 Java API 不符时，指出具体疑点，不为了套用示例引入编译错误。

## 追加约定：Controller / Service / Impl / Mapper 强制分层

以下是用户明确要求的本项目强制规则，在正文“Service 暴露接口、实现类以 Impl 结尾”的基础上明确各层职责，不改动用户提供的规范原文。

### 包结构与调用方向

```text
controller/                  XxxController：HTTP 入口
service/                     XxxService：服务接口
service/impl/                XxxServiceImpl：业务实现
mapper/                      XxxMapper：数据库访问
resources/mapper/            Mapper XML：按需编写 SQL
```

调用方向：`Controller → Service 接口 → ServiceImpl → Mapper`。Controller 注入 Service 接口，不直接注入实现类或 Mapper；数据访问层不反向依赖 Controller。

### Controller 必须薄

- 只负责接收请求、参数格式校验、取得可信登录身份、调用 Service 接口，以及响应或 SSE 的 HTTP 协议适配。
- 不编写业务规则、业务分支、业务流程编排、事务逻辑、模型与工具的业务执行逻辑。
- 不编写 SQL、查询条件 Wrapper 或数据库查询拼装，不直接调用 Mapper、DAO、Repository、JdbcTemplate 等数据访问组件。
- 不能把业务代码挪到 `controller` 的内部类或辅助类来绕过分层要求；HTTP 发送和协议转换本身不属于业务编排。

### Service 只定义接口

- `service` 包只放 `XxxService` 接口，用于声明服务能力、方法签名及 Javadoc 契约。
- 不在 Service 接口中编写业务方法体、SQL 或数据访问实现；不通过 `default` 或 `static` 方法承载业务逻辑。
- 不把带有业务实现的普通类或 `@Service` 类直接放进 `service` 包。

### Impl 承载业务实现

- 业务逻辑必须放在 `service.impl` 的 `XxxServiceImpl` 中，实现对应的 `XxxService` 接口，并在实现类上使用 `@Service`。
- 业务校验、流程编排、模型与工具的业务调用、事务处理等均由实现类负责；Controller 只调用其接口。
- Agent、同步问答、SSE 流式问答等服务也遵守此规则，不能因使用 Reactor 或 SSE 就省略 Service 接口。

### SQL 优先放 Mapper，不能高于实现层

- SQL 一般写在 Mapper XML 或 Mapper 接口的 SQL 注解中，通过 Mapper 执行；优先使用已有 Mapper 能力。
- 确有必要在 Service 层保留 SQL、查询条件构造或数据访问逻辑时，只能放在 `service.impl` 的实现类中，这是允许的最高层，不能继续上移到 Controller。
- “SQL 最多放 Service 层”不表示可以写在 `XxxService` 接口里；接口仍只定义方法契约。
- 对需要复用或较复杂的数据库查询，优先下沉 Mapper，避免在多个实现类中重复拼装 SQL。

新增或修改相关业务代码时按以上规则组织。仅要求维护规范时，不自动重构现有业务类。原文中的测试类命名条款不改变下方“不生成测试类”的用户约定。

## 追加约定：作者取当前 Git 分支创建者

- 新增或补写 Javadoc 的 `@author` 时，使用经核实的当前 Git 分支创建者名称；不得固定写 `netagent`、项目名、助手名或未经确认的占位作者。
- 先使用 `git symbolic-ref --quiet HEAD` 获取当前分支完整引用，再查看该分支的 reflog，核实创建记录中的操作者；不能直接取最新提交作者或仓库第一条提交作者。
- Git 分支没有可跨仓库可靠查询的“创建者”字段。分支 reflog 的创建记录最多证明本地引用的创建身份；`clone`、`fetch` 或从远端建立跟踪分支的操作者，不等于远端原始分支创建者。
- 创建记录缺失、已过期、身份含糊或处于 detached HEAD 时，先向用户确认作者，再填写；不把 `git config user.name` 自动当作分支创建者，也不根据分支名、目录名推断作者。
- 用户明确确认作者后使用其确认名称。不要将一次确认的姓名硬编码为所有分支、所有仓库通用的作者。
- 核实过程只读，不修改 Git 配置或提交历史。仅追加本规则时，不自动批量改写现有 Java 文件的署名。

## 追加约定：仅编译，由用户自行测试接口

以下是用户明确指定的本项目约定，在测试方式方面优先于正文中涉及测试类的内容。

- 不新增、恢复或编写自动化测试类、测试用例、测试脚本和测试资源。
- 不创建测试专用 POM，不在现有 POM 中添加测试依赖或测试插件配置，包括 JUnit、Mockito、Spring Boot Test、测试用 H2、Surefire、Failsafe 和 JaCoCo 等。
- 所有接口均由用户使用 Apifox 的 IDEA 插件自行测试，结合 IDEA Debug 排查；助手不自动发起接口请求或运行模型冒烟调用代替用户验证。
- 助手只进行必要的编译检查和编译错误处理，不主动运行 `mvn test`、`test-compile`、`verify` 等测试相关流程，不新增验证用的 main 或绕用临时脚本充当测试类。
- 保留已有业务代码与用户正在使用的普通 Debug 入口，导入本 Skill 不授权无关重构或删除。
- 修改代码后，从项目根目录执行 `mvn -B -pl netagent-starter -am compile`；本地依赖缓存齐全时可加 `-o`。修复本轮改动造成的编译错误，遇到外部阻塞如实说明。
- 不通过跳过编译、删除业务逻辑或压制编译错误来声称成功。
- 交付时说明修改范围和编译结果，接口运行效果明确交由用户验证。编译通过不等于接口、SSE、工具调用或业务流程已验证通过。
- 用户需要时提供 Apifox 的请求方法、路径、请求头、请求体和预期结果，但不自动执行。
- 除非用户后续明确变更此约定，否则不恢复测试类或测试相关 POM 配置。

## 使用示例

- `/java-development-standards 按 Controller / Service / Impl / Mapper 分层实现这个接口，编译通过即可`
- `/java-development-standards 解释这个方法的命名和注释是否符合提供的规范`
