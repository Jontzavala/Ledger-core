CREATE FUNCTION freeze_entry_id() RETURNS trigger
LANGUAGE plpgsql as $$
BEGIN
    IF NEW.entry_id IS DISTINCT FROM OLD.entry_id THEN
       RAISE EXCEPTION
            'postings.entry_id is immutable (row %): % -> %',
            OLD.id, OLD.entry_id, NEW.entry_id;
       END IF;
       RETURN NEW;
END;
$$;

CREATE TRIGGER postings_entry_id_immutable
    BEFORE UPDATE OF entry_id ON postings
    FOR EACH ROW
EXECUTE FUNCTION freeze_entry_id();