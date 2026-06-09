-- Keep issue_counters ahead of the highest existing issue number
UPDATE issue_counters ic
SET next_number = sub.next_num
FROM (
    SELECT project_id, COALESCE(MAX(issue_number), 0) + 1 AS next_num
    FROM issues
    GROUP BY project_id
) sub
WHERE ic.project_id = sub.project_id
  AND ic.next_number <= sub.next_num;
