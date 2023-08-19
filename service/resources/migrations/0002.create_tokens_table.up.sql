create table tokens(
  user_id bigint references users(id) primary key,
  access_token text not null,
  refresh_token text not null,
  token_type varchar(255) not null,
  expires_at timestamp not null,
  scope text not null
);
