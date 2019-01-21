DROP TABLE IF EXISTS backup_004;
--;;
CREATE TABLE backup_004 (
       id INTEGER NOT NULL PRIMARY KEY,
       date TEXT,
       book_id INTEGER,
       duration INTEGER,
       FOREIGN KEY (book_id) REFERENCES books(id)
);
--;;
INSERT INTO backup_004 (id, date, book_id, duration)
       SELECT id, date, book_id, duration FROM reading_sessions;
--;;
DROP TABLE reading_sessions;
--;;
CREATE TABLE reading_sessions (
       id INTEGER NOT NULL PRIMARY KEY,
       date TEXT,
       book_id INTEGER,
       duration INTEGER,
       page_count INTEGER,
       FOREIGN KEY (book_id) REFERENCES books(id)
);
--;;
INSERT INTO reading_sessions (id, date, book_id, duration)
       SELECT id, date, book_id, duration FROM backup_004;
--;;
DROP TABLE backup_004;
