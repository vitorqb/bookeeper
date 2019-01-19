CREATE TABLE reading_sessions (
       id INTEGER NOT NULL PRIMARY KEY,
       date TEXT,
       book_id INTEGER,
       duration INTEGER,
       FOREIGN KEY (book_id) REFERENCES books(id)
);
