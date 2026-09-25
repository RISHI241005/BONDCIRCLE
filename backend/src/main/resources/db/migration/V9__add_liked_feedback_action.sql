-- Migration to add LIKED feedback action to ai_reply_feedback check constraint
ALTER TABLE ai_reply_feedback DROP CHECK chk_feedback_action;
ALTER TABLE ai_reply_feedback ADD CONSTRAINT chk_feedback_action CHECK (action IN ('SHOWN','USED','REJECTED','COPIED','EDITED','SENT','LIKED'));
