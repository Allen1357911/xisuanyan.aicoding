-- ============================================================
-- 初始化数据（H2 内存库，每次启动都会重新执行）
-- 所有账号密码：
--   admin  / admin123     管理员
--   3001   / 123456       学生 张三
--   3002   / 123456       学生 李四
--   2001   / 123456       维修人员 王师傅
--   2002   / 123456       维修人员 赵师傅
-- 账号规则：3 开头注册为学生，2 开头注册为维修人员
-- ============================================================

-- 角色
insert into role (role_id, role_name, role_code) values (1, '学生', 'student');
insert into role (role_id, role_name, role_code) values (2, '管理员', 'admin');
insert into role (role_id, role_name, role_code) values (3, '维修人员', 'repairman');

-- 用户（密码均为 BCrypt 密文）
insert into user (user_id, account, password, role_id, user_name) values
    (1, 'admin', '$2a$10$xnYQEheM/YC7UbGI5XwtpOVVXqS9A9hA3DWpOubRT8d027z.MZ7um', 2, '系统管理员');
insert into user (user_id, account, password, role_id, user_name) values
    (2, '3001', '$2a$10$TAnEYesxt3d2IsA8GvFtN.w5lkrdea/k0A2z1724Lu3c3PB3K/3Da', 1, '张三');
insert into user (user_id, account, password, role_id, user_name) values
    (3, '3002', '$2a$10$TAnEYesxt3d2IsA8GvFtN.w5lkrdea/k0A2z1724Lu3c3PB3K/3Da', 1, '李四');
insert into user (user_id, account, password, role_id, user_name) values
    (4, '2001', '$2a$10$iOH.WuIPUbD58lRXWqDgne/kx.kW3xOMyN9S9iN0N1HK5akXlCVoi', 3, '王师傅');
insert into user (user_id, account, password, role_id, user_name) values
    (5, '2002', '$2a$10$iOH.WuIPUbD58lRXWqDgne/kx.kW3xOMyN9S9iN0N1HK5akXlCVoi', 3, '赵师傅');

-- 宿舍绑定
insert into dormitory (dorm_id, user_id, building, room_num) values (1, 2, '1栋', '502');
insert into dormitory (dorm_id, user_id, building, room_num) values (2, 3, '3栋', '301');

-- 报修单：覆盖待处理/维修中/已完成/已取消四种状态
insert into repair_order (order_id, user_id, dorm_id, device_type, problem_desc, order_status,
                          repairman_id, building, room_num, create_time, update_time) values
    (1, 2, 1, '水龙头', '洗手池水龙头一直滴水，拧到底也关不紧', '待处理',
     null, '1栋', '502', timestamp '2024-03-11 09:12:00', timestamp '2024-03-11 09:12:00');
insert into repair_order (order_id, user_id, dorm_id, device_type, problem_desc, order_status,
                          repairman_id, building, room_num, create_time, update_time) values
    (2, 2, 1, '电灯', '卧室吸顶灯不亮，换过灯泡仍然不亮', '维修中',
     4, '1栋', '502', timestamp '2024-03-10 20:30:00', timestamp '2024-03-12 10:05:00');
insert into repair_order (order_id, user_id, dorm_id, device_type, problem_desc, order_status,
                          repairman_id, building, room_num, create_time, update_time) values
    (3, 3, 2, '空调', '空调不制冷，开机后只出风', '已完成',
     4, '3栋', '301', timestamp '2024-03-08 14:20:00', timestamp '2024-03-09 16:40:00');
insert into repair_order (order_id, user_id, dorm_id, device_type, problem_desc, order_status,
                          repairman_id, building, room_num, create_time, update_time) values
    (4, 3, 2, '马桶', '马桶水箱一直漏水，地板总是湿的', '已取消',
     null, '3栋', '301', timestamp '2024-03-07 08:05:00', timestamp '2024-03-07 09:00:00');

-- 手工指定了主键，需要把自增序列推到最大值之后，否则后续新增数据会主键冲突
alter table role alter column role_id restart with 4;
alter table user alter column user_id restart with 6;
alter table dormitory alter column dorm_id restart with 3;
alter table repair_order alter column order_id restart with 5;
