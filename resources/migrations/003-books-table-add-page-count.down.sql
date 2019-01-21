DROP TABLE IF EXISTS books_backup;
--;;
CREATE TABLE books_backup(id INTEGER, title TEXT);
--;;
INSERT INTO books_backup SELECT id, title FROM books;
--;;
DROP TABLE books;
--;;
CREATE TABLE books(
       id INTEGER NOT NULL PRIMARY KEY,
       title TEXT NOT NULL,
       CONSTRAINT title_unique UNIQUE (title)
);
--;;
INSERT INTO books (id, title) SELECT id, title FROM books_backup;
--;;
DROP TABLE books_backup;
