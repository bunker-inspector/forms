create table users(
  id serial primary key,
  email varchar(255) unique,
  given_name VARCHAR(255) not null,
  family_name VARCHAR(255) not null,
  full_name VARCHAR(255) not null,
  picture_url text not null
);
