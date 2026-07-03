drop table if exists refunds;
drop table if exists order_items;
drop table if exists orders;
drop table if exists products;
drop table if exists users;
create table users (
  user_id bigint primary key,
  user_name varchar(64) not null
);
create table products (
  product_id bigint primary key,
  product_name varchar(64) not null,
  category varchar(32) not null
);
create table orders (
  order_id bigint primary key,
  channel varchar(32) not null,
  created_at timestamp not null
);
create table order_items (
  item_id bigint primary key,
  order_id bigint not null,
  product_id bigint not null,
  quantity int not null,
  amount decimal(12,2) not null
);
create table refunds (
  refund_id bigint primary key,
  order_id bigint not null,
  created_at timestamp not null
);
insert into users(user_id, user_name) values (1,'Alice'),(2,'Bob'),(3,'Carol');
insert into products(product_id, product_name, category) values (1,'Phone','electronics'),(2,'Desk','furniture');
insert into orders(order_id, channel, created_at) values
(1001,'organic', DATEADD('HOUR', -36, CURRENT_TIMESTAMP())),
(1002,'organic', DATEADD('HOUR', -35, CURRENT_TIMESTAMP())),
(1003,'organic', DATEADD('HOUR', -34, CURRENT_TIMESTAMP())),
(1004,'organic', DATEADD('HOUR', -33, CURRENT_TIMESTAMP())),
(1005,'organic', DATEADD('HOUR', -32, CURRENT_TIMESTAMP())),
(1006,'organic', DATEADD('HOUR', -31, CURRENT_TIMESTAMP())),
(1007,'organic', DATEADD('HOUR', -30, CURRENT_TIMESTAMP())),
(1008,'organic', DATEADD('HOUR', -29, CURRENT_TIMESTAMP())),
(1009,'paid', DATEADD('HOUR', -28, CURRENT_TIMESTAMP())),
(1010,'paid', DATEADD('HOUR', -27, CURRENT_TIMESTAMP())),
(1011,'paid', DATEADD('HOUR', -26, CURRENT_TIMESTAMP())),
(1012,'paid', DATEADD('HOUR', -25, CURRENT_TIMESTAMP())),
(2001,'organic', DATEADD('HOUR', -6, CURRENT_TIMESTAMP())),
(2002,'paid', DATEADD('HOUR', -5, CURRENT_TIMESTAMP())),
(2003,'paid', DATEADD('HOUR', -4, CURRENT_TIMESTAMP())),
(2004,'paid', DATEADD('HOUR', -3, CURRENT_TIMESTAMP())),
(2005,'paid', DATEADD('HOUR', -2, CURRENT_TIMESTAMP())),
(2006,'paid', DATEADD('HOUR', -1, CURRENT_TIMESTAMP()));
insert into order_items(item_id, order_id, product_id, quantity, amount) values
(1,1001,1,1,699.00),(2,1002,1,1,699.00),(3,1003,2,1,299.00),(4,1004,2,1,299.00),
(5,1005,1,1,699.00),(6,1006,1,1,699.00),(7,1007,2,1,299.00),(8,1008,2,1,299.00),
(9,1009,1,1,699.00),(10,1010,1,1,699.00),(11,1011,2,1,299.00),(12,1012,2,1,299.00),
(13,2001,1,1,699.00),(14,2002,1,1,699.00),(15,2003,2,1,299.00),(16,2004,2,1,299.00),
(17,2005,1,1,699.00),(18,2006,2,1,299.00);
insert into refunds(refund_id, order_id, created_at) values
(1,1011, DATEADD('HOUR', -25, CURRENT_TIMESTAMP())),
(2,2002, DATEADD('HOUR', -4, CURRENT_TIMESTAMP())),
(3,2004, DATEADD('HOUR', -3, CURRENT_TIMESTAMP())),
(4,2006, DATEADD('HOUR', -1, CURRENT_TIMESTAMP()));