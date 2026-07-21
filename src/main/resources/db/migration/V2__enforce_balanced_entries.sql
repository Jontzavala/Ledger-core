CREATE FUNCTION check_postings_balanced() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    total BIGINT;
    the_entry_id BIGINT;
BEGIN
    the_entry_id := COALESCE(NEW.entry_id, OLD.entry_id);
    SELECT COALESCE(SUM(amount), 0) INTO total
    FROM postings
    WHERE entry_id = the_entry_id;

    IF total <> 0 THEN
       RAISE EXCEPTION 'postings for journal entry % are unbalanced: sum(amount) = %',
            the_entry_id, total;
       END IF;

       RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER postings_balanced
       AFTER INSERT OR UPDATE OR DELETE ON postings
       DEFERRABLE INITIALLY DEFERRED
       FOR EACH ROW
EXECUTE FUNCTION check_postings_balanced();