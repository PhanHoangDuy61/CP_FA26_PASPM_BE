-- Legacy occurrences, if any, are not assigned invented dates or actors.
ALTER TABLE mining_schedule_occurrence ADD actor_id INT NULL;
ALTER TABLE mining_schedule_occurrence ADD range_start DATE NULL;
ALTER TABLE mining_schedule_occurrence ADD range_end DATE NULL;
ALTER TABLE mining_schedule_occurrence ADD analysis_date DATE NULL;
ALTER TABLE mining_schedule_occurrence ADD time_zone VARCHAR(100) NULL;
ALTER TABLE mining_schedule_occurrence ADD compare_algorithms BOOLEAN NULL;
ALTER TABLE mining_schedule_occurrence ADD CONSTRAINT fk_occurrence_actor FOREIGN KEY(actor_id) REFERENCES app_user(user_id);
