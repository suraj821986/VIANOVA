IF OBJECT_ID('dbo.rider_onboarding', 'U') IS NULL
CREATE TABLE dbo.rider_onboarding (
    rider_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    first_name NVARCHAR(120) NOT NULL,
    last_name NVARCHAR(120) NOT NULL,
    email NVARCHAR(255) NOT NULL,
    phone_number NVARCHAR(30) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    onboarding_status NVARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_rider_onboarding_email'
      AND object_id = OBJECT_ID('dbo.rider_onboarding')
)
CREATE UNIQUE INDEX UX_rider_onboarding_email
ON dbo.rider_onboarding(email);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_rider_onboarding_phone'
      AND object_id = OBJECT_ID('dbo.rider_onboarding')
)
CREATE UNIQUE INDEX UX_rider_onboarding_phone
ON dbo.rider_onboarding(phone_number);

IF OBJECT_ID('dbo.rider_saved_cards', 'U') IS NULL
CREATE TABLE dbo.rider_saved_cards (
    card_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    rider_id UNIQUEIDENTIFIER NOT NULL,
    card_holder_name NVARCHAR(120) NOT NULL,
    last4 NVARCHAR(4) NOT NULL,
    expiry_month INT NOT NULL,
    expiry_year INT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_rider_saved_cards_rider
        FOREIGN KEY (rider_id) REFERENCES dbo.rider_onboarding(rider_id)
);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_rider_saved_cards_dedup'
      AND object_id = OBJECT_ID('dbo.rider_saved_cards')
)
CREATE UNIQUE INDEX UX_rider_saved_cards_dedup
ON dbo.rider_saved_cards(rider_id, card_holder_name, last4, expiry_month, expiry_year);

IF OBJECT_ID('dbo.ride_requests', 'U') IS NULL
CREATE TABLE dbo.ride_requests (
    request_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    rider_id UNIQUEIDENTIFIER NOT NULL,
    assigned_driver_id UNIQUEIDENTIFIER NULL,
    source NVARCHAR(255) NOT NULL,
    destination NVARCHAR(255) NOT NULL,
    departure_time DATETIME2 NOT NULL,
    estimated_fare DECIMAL(10,2) NOT NULL,
    currency NVARCHAR(10) NOT NULL DEFAULT 'USD',
    status NVARCHAR(40) NOT NULL DEFAULT 'PENDING_DRIVER',
    rider_offer DECIMAL(10,2) NULL,
    counter_offer DECIMAL(10,2) NULL,
    rider_final_acceptance BIT NOT NULL DEFAULT 0,
    driver_final_acceptance BIT NOT NULL DEFAULT 0,
    accepted_at DATETIME2 NULL,
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_ride_requests_rider
        FOREIGN KEY (rider_id) REFERENCES dbo.rider_onboarding(rider_id),
    CONSTRAINT FK_ride_requests_driver
        FOREIGN KEY (assigned_driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF COL_LENGTH('dbo.ride_requests', 'rider_offer') IS NULL
ALTER TABLE dbo.ride_requests ADD rider_offer DECIMAL(10,2) NULL;

IF COL_LENGTH('dbo.ride_requests', 'counter_offer') IS NULL
ALTER TABLE dbo.ride_requests ADD counter_offer DECIMAL(10,2) NULL;

IF COL_LENGTH('dbo.ride_requests', 'rider_final_acceptance') IS NULL
ALTER TABLE dbo.ride_requests ADD rider_final_acceptance BIT NOT NULL DEFAULT 0;

IF COL_LENGTH('dbo.ride_requests', 'driver_final_acceptance') IS NULL
ALTER TABLE dbo.ride_requests ADD driver_final_acceptance BIT NOT NULL DEFAULT 0;

IF COL_LENGTH('dbo.ride_requests', 'accepted_at') IS NULL
ALTER TABLE dbo.ride_requests ADD accepted_at DATETIME2 NULL;

IF COL_LENGTH('dbo.ride_requests', 'updated_at') IS NULL
ALTER TABLE dbo.ride_requests ADD updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME();

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_ride_requests_status_created'
      AND object_id = OBJECT_ID('dbo.ride_requests')
)
CREATE INDEX IX_ride_requests_status_created
ON dbo.ride_requests(status, created_at DESC);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_ride_requests_driver_status'
      AND object_id = OBJECT_ID('dbo.ride_requests')
)
CREATE INDEX IX_ride_requests_driver_status
ON dbo.ride_requests(assigned_driver_id, status, updated_at DESC);
