create table orders (
    id uuid primary key,
    customer_email varchar(255) not null,
    status varchar(50) not null,
    total_amount numeric(12, 2) not null,
    created_at timestamp with time zone not null
);
