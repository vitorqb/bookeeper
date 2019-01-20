CREATE TABLE books (
       id INTEGER NOT NULL PRIMARY KEY,
       title TEXT NOT NULL,
       CONSTRAINT title_unique UNIQUE (title)
);
