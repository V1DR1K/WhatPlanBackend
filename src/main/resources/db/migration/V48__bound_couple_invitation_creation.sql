-- Count all invite generations in the rolling quota window without scanning history.
CREATE INDEX idx_couple_invitations_couple_created_at
  ON couple_invitations(couple_id, created_at DESC);
