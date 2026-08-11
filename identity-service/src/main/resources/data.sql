-- Five deterministic development accounts. The system admin remains managed
-- by ApplicationInitConfig and is intentionally not counted as a social user.
-- Development password for all five users: admin

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `account_seed_metadata` (
  `seed_key` varchar(100) NOT NULL,
  `applied_at` datetime(6) NOT NULL,
  PRIMARY KEY (`seed_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @account_seed_required = NOT EXISTS (
  SELECT 1 FROM `account_seed_metadata` WHERE `seed_key` = 'social-users-2026-08-10'
);

INSERT IGNORE INTO `role` (`name`, `description`)
SELECT 'USER', 'Người dùng thông thường' FROM DUAL WHERE @account_seed_required = 1;

INSERT IGNORE INTO `users` (`id`, `active`, `email`, `password`, `username`)
SELECT '10000000-0000-0000-0000-000000000001', TRUE, 'minhan@example.test', '$2a$10$QnjNfnnbsWQY0Xxy8kkNY.2qN3imeeYdkc2PXMCiGKbIcMiJ2jkha', 'nguyenminhan'
FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users` (`id`, `active`, `email`, `password`, `username`)
SELECT '10000000-0000-0000-0000-000000000002', TRUE, 'thulan@example.test', '$2a$10$QnjNfnnbsWQY0Xxy8kkNY.2qN3imeeYdkc2PXMCiGKbIcMiJ2jkha', 'tranthulan'
FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users` (`id`, `active`, `email`, `password`, `username`)
SELECT '10000000-0000-0000-0000-000000000003', TRUE, 'quanghuy@example.test', '$2a$10$QnjNfnnbsWQY0Xxy8kkNY.2qN3imeeYdkc2PXMCiGKbIcMiJ2jkha', 'lequanghuy'
FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users` (`id`, `active`, `email`, `password`, `username`)
SELECT '10000000-0000-0000-0000-000000000004', TRUE, 'ngocmai@example.test', '$2a$10$QnjNfnnbsWQY0Xxy8kkNY.2qN3imeeYdkc2PXMCiGKbIcMiJ2jkha', 'phamngocmai'
FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users` (`id`, `active`, `email`, `password`, `username`)
SELECT '10000000-0000-0000-0000-000000000005', TRUE, 'vanphuc@example.test', '$2a$10$QnjNfnnbsWQY0Xxy8kkNY.2qN3imeeYdkc2PXMCiGKbIcMiJ2jkha', 'dovanphuc'
FROM DUAL WHERE @account_seed_required = 1;

INSERT IGNORE INTO `users_roles` (`user_id`, `roles_name`)
SELECT '10000000-0000-0000-0000-000000000001', 'USER' FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users_roles` (`user_id`, `roles_name`)
SELECT '10000000-0000-0000-0000-000000000002', 'USER' FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users_roles` (`user_id`, `roles_name`)
SELECT '10000000-0000-0000-0000-000000000003', 'USER' FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users_roles` (`user_id`, `roles_name`)
SELECT '10000000-0000-0000-0000-000000000004', 'USER' FROM DUAL WHERE @account_seed_required = 1;
INSERT IGNORE INTO `users_roles` (`user_id`, `roles_name`)
SELECT '10000000-0000-0000-0000-000000000005', 'USER' FROM DUAL WHERE @account_seed_required = 1;

INSERT IGNORE INTO `account_seed_metadata` (`seed_key`, `applied_at`)
SELECT 'social-users-2026-08-10', CURRENT_TIMESTAMP(6) FROM DUAL WHERE @account_seed_required = 1;

SET FOREIGN_KEY_CHECKS = 1;
