CREATE TABLE apirelease(
buildtime date,
version varchar(30) primary key,
links varchar(30), methods varchar(30));

CREATE TABLE book(
id INTEGER PRIMARY KEY AUTOINCREMENT,
barcode varchar(30),
title varchar(30),
author varchar(30),
release_date date,
price decimal(5,2),
publisher varchar(30),
description varchar(100),
thumbnail BLOB
);

CREATE TABLE book_images(
id INTEGER PRIMARY KEY AUTOINCREMENT,
book_id int,
image_url varchar(100),
FOREIGN KEY (book_id) REFERENCES book(id)
);
