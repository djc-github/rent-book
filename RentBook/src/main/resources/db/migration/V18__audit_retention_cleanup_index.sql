create index if not exists idx_data_change_logs_created
    on data_change_logs(created_at, id);

comment on index idx_data_change_logs_created is '支持按保留期限清理行级变更日志';
