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
