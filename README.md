# 电视安全锁 · Android TV（v1.1）

> 仓库：**https://github.com/zhubing3170/TvSafetyLock**（Public，在线题库需要公开访问）

> 装在安卓电视/机顶盒上的家长管控工具：孩子看电视时，每 5/10/15/20 分钟全屏弹一道小学题目，**答对才能继续看**。全程遥控器操作，家长用 4 位密码管理设置。
>
> **v1.1 新增三件事**：题库在线更新、关机重启不再重置计时、GitHub Actions 自动打包 APK。

---

## 一、需求对照表

| 你的要求 | 实现情况 | 在哪段代码 |
|---|---|---|
| 小学 1～6 年级 | 六档一键切换，题目按年级出 | `ui/SettingsScreen.kt` |
| 语文/数学/英语可选 | 三科可多选，至少保留一科 | `data/QuestionBank.kt` |
| 5/10/15/20 分钟四档，默认 10 | 完全按此实现，默认 10 分钟 | `data/Prefs.kt` / `service/LockClock.kt` |
| 家长密码保护 | 4 位数字密码，加盐哈希存储；连错 5 次锁 60 秒 | `data/Prefs.kt` / `ui/PinGateScreen.kt` |
| 遥控器答题 | 方向键选、OK 键确认，界面焦点高亮 | `ui/QuizScreen.kt` |
| 安卓系统 | minSdk 23（安卓 6.0 起），电视/盒子/手机平板都能装 | `app/build.gradle.kts` |
| U 盘安装 | 出 APK，拷到 U 盘插电视装，约 5 分钟 | 见第五节 |
| 3 天免费试用 | 本地计时 3 天，改系统时间也不会延长 | `license/License.kt` |
| **题库在线更新**（v1.1） | 改 GitHub 仓库里的 JSON 就能换题，不用重装 APK | `data/QuestionUpdate.kt` |
| **关机重启不重置计时**（v1.1） | 累计观看时长持久化，断电/重启/改系统时间都绕不过 | `service/LockClock.kt` |
| **GitHub 上打包**（v1.1） | push 代码自动出 APK，不用装 Android Studio | `.github/workflows/build-apk.yml` |

**额外做的（你没提但必须有）：**
- 开机自启 + 前台服务保活 + 无障碍拦截主页键
- 答题中途断电 → 开机补弹这道题；3 分钟内反复开关机 → 直接弹题
- 答错显示正确答案并换新题；连错 3 次本次锁 2 分钟
- 家长临时跳过入口、暂停管控 30 分钟、离线激活码
- 在线题库双地址容灾 + 内置题库兜底（断网照样有题出）

---

## 二、v1.1 三个改动详解

### 1. 题库在线更新

| 环节 | 做法 |
|---|---|
| 题库放哪 | 仓库根目录 `question-bank/questions.json`（语文/英语各 48 题，共 96 题），`assets/questions.json` 是出厂兜底 |
| 地址从哪来 | 打包时通过 `-PquestionBankUrl` 烧进 `BuildConfig`，电视上**不需要输网址** |
| 主地址 | `https://raw.githubusercontent.com/zhubing3170/TvSafetyLock/main/question-bank/questions.json` |
| 备用地址 | `https://cdn.jsdelivr.net/gh/zhubing3170/TvSafetyLock@main/question-bank/questions.json`（国内电视访问 GitHub raw 经常不通，自动切换） |
| 触发方式 | 设置页「立即更新题库」手动拉；或开机后自动检查，每天最多一次 |
| 安全措施 | 下载 → 逐题校验（年级/题干/选项/答案下标/重复选项）→ 写临时文件 → 再校验 → 才替换。任何一步失败都保留旧题库 |
| 离线兜底 | 内置的 `assets/questions.json` 永远在，网络断了自动回退；数学题是程序现算的，完全不受影响 |

设置页的「在线题库」区有三张卡片：**立即更新题库** / **自动更新：开/关** / **恢复内置题库**，都能用遥控器操作。

### 2. 关机重启不再重置计时（重点加固）

**你问得对，v1.0 确实有问题。** 旧版 `BootReceiver` 开机时写了 `nextFireAt = 现在 + 一个完整间隔`，还把「正在答题」的标记清成 false。结果是：孩子只要重启一次电视就白赚 10 分钟；题目刚弹出来直接断电，开机后连这道题都跳过了。

v1.1 把计时模型整个换掉：

| | v1.0 | v1.1 |
|---|---|---|
| 判定依据 | 墙钟时间戳 `nextFireAt` | 累计观看时长 `watchMs`（持久化） |
| 时钟 | `System.currentTimeMillis()`，改系统时间就能绕过 | `SystemClock.elapsedRealtime()`，开机后单调递增，不受改时间影响 |
| 关机再开机 | 重新给一个完整间隔 | **剩余时长原样保留，接着走** |
| 题目没答完就断电 | 标记被清掉，等于跳过 | 开机立刻补弹这道题（延迟 12 秒避开系统启动） |
| 反复开关机 | 每次都白赚一个间隔 | 3 分钟内连着 3 次 → 直接弹题 |
| 家长改间隔 | 重新计时 | 不清零累计；把 20 分钟改成 5 分钟时若已超时，下次心跳立刻弹题 |

补充说明：
- **关机期间不计时**（孩子没在看，不该扣时间），但也不会「送」时间。
- **隔夜正常关机**（超过 2 小时）开机时最多给 2 分钟缓冲，免得一开电视就被题目糊脸；这 2 分钟是从剩余时长里给的，不是重置成完整间隔。
- 关机时间点不依赖关机广播，靠 15 秒一次的心跳推断，**直接拔电源也算得出来**。
- 只监听解锁后的 `BOOT_COMPLETED`，不碰 `LOCKED_BOOT_COMPLETED`（那个阶段读写 SharedPreferences 会崩）。

### 3. GitHub Actions 自动打包

push 代码就自动编译出 debug + release 两个 APK，下载即用；打 `v*` tag 还会自动建 Release。CI 里用独立安装的 Gradle 8.2，绕开本机缺失的 `gradle-wrapper.jar`。

---

## 三、工程结构

```
TvSafetyLock/
├── .github/workflows/build-apk.yml   ★ GitHub Actions 自动打包
├── .gitignore                        忽略 build/、keystore、APK
├── question-bank/questions.json      ★ 在线题库源文件（改这个 + version 就能远程换题）
├── scripts/validate-questions.js    题库校验（CI 和本地都能跑，零依赖）
├── settings.gradle.kts              仓库配置（CI 走官方源，本机走阿里云镜像）
├── build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── 激活码生成器.html                 双击打开就能给用户发激活码（离线）
└── app/
    ├── build.gradle.kts              包名、签名、BuildConfig 注入题库地址与激活码密钥
    └── src/main/
        ├── AndroidManifest.xml       权限、服务、开机/关机广播、INTERNET
        ├── assets/questions.json     内置兜底题库
        ├── res/...
        └── java/com/tvsafelock/app/
            ├── App.kt / MainActivity.kt / QuizActivity.kt
            ├── data/
            │   ├── Prefs.kt              所有设置与计时状态的读写
            │   ├── Question.kt / MathGen.kt
            │   ├── QuestionBank.kt       在线优先、内置兜底、抽题
            │   └── QuestionUpdate.kt     ★ 下载/校验/原子落盘/自动检查
            ├── license/License.kt       试用判定 + 激活码算法（密钥由打包注入，不在源码里）
            ├── service/
            │   ├── LockClock.kt          ★ 抗重启计时核心
            │   ├── LockService.kt        前台服务：心跳、补弹、题库自动检查
            │   ├── QuizLauncher.kt       弹出答题页
            │   ├── BootReceiver.kt       开机恢复 / 关机心跳
            │   └── LockAccessibilityService.kt
            └── ui/
                ├── SettingsScreen.kt / SetupScreen.kt / PinGateScreen.kt
                ├── BankPanel.kt           ★ 设置页的「在线题库」区
                ├── ActivationScreen.kt / QuizScreen.kt
                └── Theme.kt / Common.kt / Perms.kt
```

---

## 四、打包 APK

### 方式 A：GitHub Actions（推荐，本机不用装 Android Studio）

**仓库地址**：https://github.com/zhubing3170/TvSafetyLock（需设为 **Public**；若还没创建，在 GitHub 点 New repository 建一个空仓库即可）。
私有仓库的 `raw.githubusercontent.com` / jsDelivr 链接要鉴权，电视上取不到，在线题库会失效，所以必须公开。
源码里的激活码密钥已经改成打包时注入，公开仓库不会泄露密钥。

**以后要改代码**：

```bash
git clone https://github.com/zhubing3170/TvSafetyLock.git
cd TvSafetyLock
# 改完文件后
git add .
git commit -m "说明改了什么"
git push
```

push 到 `main` 会自动触发打包；也可以直接在 GitHub 网页上编辑文件，同样会触发。
`.gitignore` 已经排除了 `*.jks`、`*.apk`、`build/`、`local.properties`，签名文件不会被传上去。

**发版前先配密钥**（重要，不配也能出包但用的是演示密钥）：
仓库 `Settings → Secrets and variables → Actions → New repository secret`，
名字填 `LICENSE_SECRET`，值填一串只有你知道的随机串（20 位以上，字母数字即可）。
配好后重新跑一次 Actions，APK 里就是你的密钥了；生成器页面填同一个值即可发码。

**第 3 步：等 Actions 跑完（首次约 8～15 分钟）**
仓库页 → `Actions` → 点这次运行 → 页面下方 `Artifacts` → 下载 `TvSafetyLock-APK-xxx`。里面有：

```
TvSafetyLock-debug.apk     调试包
TvSafetyLock-release.apk   发布包（拷 U 盘用这个）
tvlock.apk                 同上，短文件名方便电视识别
```

**第 4 步（可选）：打 tag 自动发 Release**

```bash
git tag v1.1.0
git push origin v1.1.0
```

会额外生成一个 Release，APK 直接挂在 Release 附件里，方便发给别人。

**打包时自动做了这些事：**
1. 校验两份题库 JSON，有一道题格式不对就直接断构建
2. 把当前仓库的题库主地址 + jsDelivr 备用地址烧进 APK
3. 同时编出 debug / release 两个包
4. 缓存 CI 生成的调试签名，保证多次构建能覆盖安装

> 想手动触发：仓库 `Actions` 页选「打包电视安全锁 APK」→ `Run workflow`。

### 方式 B：本地 Android Studio

1. 装 Android Studio：`https://developer.android.google.cn/studio`，首次启动把 SDK 下完
2. `File → Open` 选中 `TvSafetyLock` 文件夹，等 Gradle Sync
3. 提示 `Gradle wrapper not found` 时，在底部 Terminal 执行 `gradle wrapper --gradle-version 8.2`，或点提示让它自动补
4. `Build → Build Bundle(s) / APK(s) → Build APK(s)`，产物在 `app/build/outputs/apk/debug/app-debug.apk`

**本地打包要自己填题库地址**（GitHub Actions 才会自动注入）：编辑根目录 `gradle.properties`：

```properties
questionBankUrl=https://raw.githubusercontent.com/zhubing3170/TvSafetyLock/main/question-bank/questions.json
questionBankUrlMirror=https://cdn.jsdelivr.net/gh/zhubing3170/TvSafetyLock@main/question-bank/questions.json
```

留空也能打包，只是 App 里没有在线更新功能，设置页会提示「未配置更新地址」。

### 正式签名（要长期发版就配）

不配的话 CI 用缓存的调试签名，家用没问题，但**缓存被清掉时签名会变，电视上覆盖安装会失败**。正式发版建议配一次：

```bash
# 生成 keystore（自己保管，别传 GitHub）
keytool -genkeypair -v -keystore my-release-key.jks -alias tvsafelock \
  -keyalg RSA -keysize 2048 -validity 10000

# 转成一行 base64（Git Bash / Linux）
openssl base64 -A -in my-release-key.jks -out keystore.b64
```

在仓库 `Settings → Secrets and variables → Actions` 里加 Secret：

| Secret | 值 | 必需 |
|---|---|---|
| `LICENSE_SECRET` | 你自己的激活码密钥（随机串，20 位以上） | **正式发版必配** |
| `KEYSTORE_BASE64` | `keystore.b64` 的内容 | 长期发版建议 |
| `KEYSTORE_PASSWORD` | keystore 密码 | 配了 keystore 才需要 |
| `KEY_PASSWORD` | key 密码 | 配了 keystore 才需要 |
| `KEY_ALIAS` | `tvsafelock` | 配了 keystore 才需要 |

配好 keystore 后 CI 会自动把 jks 写进 `app/my-release-key.jks`，release 包就是正式签名。
本机打包则把 jks 放进 `app/`，再这样传密钥（别写进 `gradle.properties`，那文件是公开的）：

```bash
gradle assembleRelease -PLICENSE_SECRET=你的密钥 -PKEYSTORE_PASSWORD=xxx -PKEY_PASSWORD=xxx -PKEY_ALIAS=tvsafelock
```

密钥也可以通过环境变量 `LICENSE_SECRET` 提供，优先级：`-P` 参数 → 环境变量 → 演示密钥。

---

## 五、装到电视上（约 5 分钟）

1. 把 `TvSafetyLock-release.apk` 拷到 U 盘根目录，文件名建议改成英文 `tvlock.apk`
2. U 盘插电视，用文件管理找到 APK 点安装
3. 提示「禁止安装未知应用」→ 去设置里允许该文件管理器安装应用
   - 小米电视：`设置 → 账号与安全 → 允许安装未知来源的应用`
   - 海信/聚好看：`设置 → 更多设置 → 通用 → 安全 → 未知来源应用安装`
4. 装完在「我的应用」里打开「电视安全锁」

---

## 六、家长首次设置（电视上 3 分钟）

引导页四张卡片：

| 卡片 | 必做？ | 说明 |
|---|---|---|
| ① 允许悬浮窗 | **必做** | 跳到系统设置，打开「显示在其他应用上层」 |
| ② 开启无障碍 | 强烈建议 | 跳到「无障碍 → 已下载的服务」打开。**能拦住遥控器主页键** |
| ③ 允许后台常驻 | **必做** | 跳过电池优化，不然计时会被系统杀掉 |
| ④ 通知权限 | 可选 | 只是显示运行状态 |

①③ 变绿后「下一步」亮起 → 设置 4 位家长密码 → 完成。设置页可调年级（默认 3）、科目（默认语文+数学）、间隔（默认 10 分钟），点「弹一道试试」验证效果。

**国产电视必须多做一步**：`设置 → 应用管理 → 电视安全锁` → 自启动允许、后台运行允许、省电策略无限制。小米/海信/创维/TCL 不做这步，重启后可能不弹题。

---

## 七、在线题库怎么更新（改题不用重装）

1. 在 GitHub 网页上直接编辑 `question-bank/questions.json`，加题格式：
   ```json
   {"g":3,"q":"题干","o":["选项A","选项B","选项C","选项D"],"a":0}
   ```
   `g` = 年级 1~6，`a` = 正确项下标（0 是第一个）
2. **把文件开头的 `"version": 1` 改成 `2`**（不改版本号 App 会认为是旧版，不更新）
3. 顺手把 `"updated"` 改成当天日期
4. 提交 push。可以先在本地跑一遍校验：
   ```bash
   node scripts/validate-questions.js question-bank/questions.json
   ```
5. 电视上：`家长设置 → 在线题库 → 立即更新题库`；或者重启电视，开机后会自动检查（每天最多一次）
6. 看到「已更新到 v2，共 N 题」就成功了，下一道题立刻用新题库

**几个注意点：**
- 仓库必须是 Public，且默认分支名要和打包时一致（workflow 用的是 `github.event.repository.default_branch`）
- jsDelivr 有 CDN 缓存，刚 push 完可能要等几分钟；App 主地址优先，不通才走 CDN
- 在线题库坏掉了怎么办：设置页点「恢复内置题库」，立刻回到出厂 96 题
- 想加更多题：语文/英语每年级目前只有 8 题，孩子容易背答案，建议补到每科每年级 50 题以上。数学题是程序按年级现算的，无限量
- 想改内置题库：编辑 `app/src/main/assets/questions.json` 后重新打包

---

## 八、怎么给用户发激活码

1. 用户在电视上打开 `家长设置 → 激活 / 试用信息`，看到 8 位「设备码」
2. 用户把设备码发给你
3. 你双击打开 `激活码生成器.html`，**先在「激活码密钥」里填 `LICENSE_SECRET` 的值**，再粘设备码点「生成激活码」
4. 得到 12 位数字发给用户，用户在电视上用遥控器数字键输入，点「确认激活」

生成器完全离线，不联网、不上传，也不保存密钥（刷新即清空）。这里填的密钥必须和打包那个 APK 时的 `LICENSE_SECRET` 完全一致，否则生成的码无效。源码和页面里都没有写死密钥，公开仓库不会泄露。

---

## 九、发版前必改清单

| 改什么 | 文件 | 说明 |
|---|---|---|
| **密钥**（最重要） | GitHub 仓库 Secret `LICENSE_SECRET` | 换成自己的随机串；生成器页面填同一个值；改完全部旧激活码失效。**不要写进源码** |
| **包名** | `app/build.gradle.kts` | `applicationId` 和 `namespace` 改成你自己的 |
| **GitHub 仓库** | `zhubing3170/TvSafetyLock` | 保持 Public；workflow 会自动把题库地址烧进包里 |
| **正式签名** | GitHub Secrets | 见第四节，长期发版必配 |
| **题库版本号** | `question-bank/questions.json` | 每次改题记得 `version + 1` |

---

## 十、常见问题

**Q：关机再开机，是不是又要等 10 分钟？**
不是了。v1.1 起剩余时长会保留，比如关机前还剩 3 分钟，开机后接着这 3 分钟走（隔夜关机最多给 2 分钟缓冲）。题目没答完就断电，开机还会把这道题补回来。

**Q：孩子改系统时间能绕过吗？**
不能。计时用 `SystemClock.elapsedRealtime()`（开机后单调递增），根本不看系统时间。试用期的累计用时也是只增不减。

**Q：在线题库点了没反应 / 提示下载失败？**
按顺序查：仓库是不是 Public → 电视能不能上网 → 设置页顶部有没有显示「未配置在线题库地址」（有的说明是本地打包且没填 `gradle.properties`）→ 换个网络试试。失败时旧题库会原样保留，不影响答题。

**Q：改了 question-bank 里的题，电视上没变？**
九成是忘了把 `version` +1。另外 jsDelivr 有缓存，刚 push 完等几分钟再点「立即更新题库」。

**Q：覆盖安装提示「应用未安装 / 签名冲突」？**
两次 APK 的签名不一样。用同一个 GitHub 仓库打包（调试签名已缓存）或配好正式签名 Secrets 即可；实在不行先卸载重装（会清掉密码和设置）。

**Q：装上了但不弹题？**
设置页顶部徽章是不是「运行中」→ 悬浮窗权限给了没 → 电池优化放行了没 → 系统设置里自启动/后台运行放行 → 点「弹一道试试」能弹说明是保活问题，不能弹是悬浮窗权限问题。

**Q：孩子按主页键还是能跑掉？**
无障碍没开。去 `设置 → 无障碍 → 电视安全锁` 打开，部分电视需要重启生效。

**Q：家长密码忘了？**
只能卸载重装（数据会清空）。这是刻意设计，不然孩子也能想办法重置。

---

## 十一、已知限制

1. **不能上应用商店**：这类要悬浮窗 + 无障碍的管控 App，Google Play 审核基本过不了，国内商店普遍要企业资质。U 盘分发正好绕开。
2. **3 天试用是本地判断**：改系统时间不会延长，但**卸载重装会重置试用**。要真正防破解得联网校验，成本另说。
3. **在线题库依赖公网**：仓库必须 Public，且电视要能访问 GitHub raw 或 jsDelivr；都通不了时自动用内置题库，功能不瘫但没有新题。
4. **iOS 做不了**：盖住别的 App、拦系统按键这些能力 iOS 不给第三方权限。
5. **极少数电视不支持无障碍按键拦截**（系统裁剪过），装完用「弹一道试试」验证。
6. **本机没跑过 Gradle 编译**：交付前做了括号平衡检查、题库 JSON 校验、workflow 语法与参数一致性检查；首次真正编译建议直接用 GitHub Actions 跑一遍，有报错把日志贴过来即可。
