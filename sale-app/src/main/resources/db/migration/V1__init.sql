CREATE TABLE program (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(160) NOT NULL,
    venue VARCHAR(160) NOT NULL,
    show_time DATETIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    INDEX idx_program_name (name),
    INDEX idx_program_status_time (status, show_time)
);

CREATE TABLE seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    seat_no VARCHAR(32) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    UNIQUE KEY uk_program_seat (program_id, seat_no),
    INDEX idx_seat_program_status (program_id, status)
);

CREATE TABLE sale_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    paid_at DATETIME NULL,
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_order_user (user_id),
    INDEX idx_order_status_created (status, created_at)
);

CREATE TABLE order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_no VARCHAR(32) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    UNIQUE KEY uk_order_seat (order_no, seat_id),
    INDEX idx_item_order (order_no)
);

INSERT INTO program(name, venue, show_time, status)
VALUES ('原创示例音乐会', '示例体育馆', DATE_ADD(NOW(), INTERVAL 30 DAY), 'ON_SALE');

INSERT INTO seat(program_id, seat_no, price, status) VALUES
(1, 'A-001', 199.00, 'AVAILABLE'),
(1, 'A-002', 199.00, 'AVAILABLE'),
(1, 'A-003', 199.00, 'AVAILABLE'),
(1, 'B-001', 299.00, 'AVAILABLE'),
(1, 'B-002', 299.00, 'AVAILABLE');
