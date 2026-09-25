CREATE DATABASE IF NOT EXISTS netagent;

CREATE TABLE t_user (
                        id           VARCHAR(20)  NOT NULL PRIMARY KEY,
                        username     VARCHAR(64)  NOT NULL,
                        password     VARCHAR(128) NOT NULL,
                        role         VARCHAR(32)  NOT NULL,
                        avatar       VARCHAR(128),
                        create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
                        update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
                        deleted      SMALLINT     DEFAULT 0,
                        CONSTRAINT uk_user_username UNIQUE (username)
);
COMMENT ON TABLE t_user IS '系统用户表';
COMMENT ON COLUMN t_user.id IS '主键ID';
COMMENT ON COLUMN t_user.username IS '用户名，唯一';
COMMENT ON COLUMN t_user.password IS '密码';
COMMENT ON COLUMN t_user.role IS '角色：admin/user';
COMMENT ON COLUMN t_user.avatar IS '用户头像';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '是否删除 0：正常 1：删除';

