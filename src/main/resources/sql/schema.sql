-- ============================================================
-- 宿舍报修系统 建表脚本（H2 内存数据库 / MySQL 兼容模式）
-- 由原 MySQL 建表语句移植而来，结构与字段保持一致，差异如下：
--   1. 去掉了 MySQL 专有的 inline COMMENT 与表级 comment，改为行注释
--   2. 去掉了 update_time 上的 "on update CURRENT_TIMESTAMP"：
--      H2 不支持该写法，且项目里 update_time 由 MyBatis-Plus 的自动填充
--      （MyBatisMetaObjectHandler）与业务代码显式维护，不依赖数据库触发器
--   3. 独立索引改名加 idx_ 前缀：MySQL 的索引名是表级的，H2 是 schema 级的，
--      原名会和唯一约束重名
-- 脚本开头先删表，保证在同一个 JVM 内重复执行（例如跑测试）也不会冲突
-- ============================================================

drop table if exists order_image;
drop table if exists repair_order;
drop table if exists dormitory;
drop table if exists user;
drop table if exists role;

-- 角色表：1-学生 2-管理员 3-维修人员
create table role
(
    role_id     int auto_increment primary key,          -- 角色主键ID
    role_name   varchar(20) not null,                    -- 角色名称：学生/管理员/维修人员
    role_code   varchar(10) not null,                    -- 角色编码：student/admin/repairman
    create_time datetime default CURRENT_TIMESTAMP,      -- 创建时间
    update_time datetime default CURRENT_TIMESTAMP,      -- 更新时间
    constraint role_code unique (role_code),
    constraint role_name unique (role_name)
);

-- 用户表：学生与维修人员可自助注册，管理员由系统预置
create table user
(
    user_id     bigint auto_increment primary key,       -- 用户主键ID
    account     varchar(20)  not null,                   -- 账号：学生学号/维修工号
    password    varchar(100) not null,                   -- 密码（BCrypt 加密存储）
    role_id     int          not null,                   -- 关联角色表ID
    user_name   varchar(30) default '未知',               -- 用户姓名
    create_time datetime    default CURRENT_TIMESTAMP,   -- 创建时间
    update_time datetime    default CURRENT_TIMESTAMP,   -- 更新时间
    constraint account unique (account),
    constraint user_ibfk_1 foreign key (role_id) references role (role_id) on update cascade
);

create index idx_user_role_id on user (role_id);

-- 宿舍绑定表：一个学生仅绑定一个宿舍
create table dormitory
(
    dorm_id     bigint auto_increment primary key,       -- 宿舍主键ID
    user_id     bigint      not null,                    -- 关联用户表ID
    building    varchar(20) not null,                    -- 宿舍楼栋：如1栋/东区5栋
    room_num    varchar(10) not null,                    -- 房间号：如502/301-2
    create_time datetime default CURRENT_TIMESTAMP,      -- 绑定时间
    update_time datetime default CURRENT_TIMESTAMP,      -- 修改时间
    constraint user_id unique (user_id),
    constraint dormitory_ibfk_1 foreign key (user_id) references user (user_id) on update cascade on delete cascade
);

-- 报修单表
create table repair_order
(
    order_id     bigint auto_increment primary key,      -- 报修单主键ID
    user_id      bigint      not null,                   -- 关联报修用户ID
    dorm_id      bigint      not null,                   -- 关联宿舍ID
    device_type  varchar(30) not null,                   -- 设备类型：如水龙头/电灯/空调/马桶
    problem_desc varchar(2000) not null,                 -- 问题描述（原库为 text，H2 用长 varchar 等价实现）
    order_status varchar(20) default '待处理' not null,   -- 状态：待处理/维修中/已完成/已取消
    create_time  datetime    default CURRENT_TIMESTAMP,  -- 报修创建时间
    update_time  datetime    default CURRENT_TIMESTAMP,  -- 状态最后修改时间
    repairman_id bigint,                                 -- 接单的维修人员ID
    building     varchar(20),                            -- 楼栋（下单时冗余保存，便于展示）
    room_num     varchar(10),                            -- 房间号（同上）
    constraint repair_order_ibfk_1 foreign key (user_id) references user (user_id) on update cascade on delete cascade,
    constraint repair_order_ibfk_2 foreign key (dorm_id) references dormitory (dorm_id) on update cascade on delete cascade
);

create index idx_repair_order_user_id on repair_order (user_id);

-- 报修单图片表（每个订单最多 3 张，在业务层限制）
create table order_image
(
    id          bigint auto_increment primary key,
    order_id    bigint       not null,
    image_url   varchar(255) not null,
    create_time datetime default CURRENT_TIMESTAMP not null,
    constraint order_image_ibfk_1 foreign key (order_id) references repair_order (order_id) on delete cascade
);

create index idx_order_image_order_id on order_image (order_id);
