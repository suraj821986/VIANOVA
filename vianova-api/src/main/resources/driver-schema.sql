IF OBJECT_ID('dbo.driver_onboarding', 'U') IS NULL
CREATE TABLE dbo.driver_onboarding (
    driver_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    name NVARCHAR(120) NOT NULL,
    age INT NOT NULL CHECK (age >= 18),
    sex NVARCHAR(20) NOT NULL,
    social_address NVARCHAR(255) NOT NULL,
    experience_years INT NOT NULL CHECK (experience_years >= 0),
    email NVARCHAR(255) NOT NULL,
    phone_number NVARCHAR(30) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    driving_license_number NVARCHAR(80) NOT NULL UNIQUE,
    license_document VARBINARY(MAX) NOT NULL,
    license_document_name NVARCHAR(255) NOT NULL,
    license_document_content_type NVARCHAR(100) NULL,
    onboarding_status NVARCHAR(40) NOT NULL DEFAULT 'PENDING_CLEARANCE',
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);

IF COL_LENGTH('dbo.driver_onboarding', 'email') IS NULL
ALTER TABLE dbo.driver_onboarding ADD email NVARCHAR(255) NULL;

IF COL_LENGTH('dbo.driver_onboarding', 'phone_number') IS NULL
ALTER TABLE dbo.driver_onboarding ADD phone_number NVARCHAR(30) NULL;

IF COL_LENGTH('dbo.driver_onboarding', 'password_hash') IS NULL
ALTER TABLE dbo.driver_onboarding ADD password_hash NVARCHAR(255) NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_driver_onboarding_email'
      AND object_id = OBJECT_ID('dbo.driver_onboarding')
)
CREATE UNIQUE INDEX UX_driver_onboarding_email
ON dbo.driver_onboarding(email)
WHERE email IS NOT NULL;

IF OBJECT_ID('dbo.driver_previous_rides', 'U') IS NULL
CREATE TABLE dbo.driver_previous_rides (
    ride_id NVARCHAR(40) NOT NULL PRIMARY KEY,
    driver_id UNIQUEIDENTIFIER NOT NULL,
    source NVARCHAR(255) NOT NULL,
    destination NVARCHAR(255) NOT NULL,
    fare DECIMAL(10,2) NOT NULL,
    ride_date DATE NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_driver_previous_rides_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF OBJECT_ID('dbo.driver_rating_summary', 'U') IS NULL
CREATE TABLE dbo.driver_rating_summary (
    driver_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    overall_rating DECIMAL(3,2) NOT NULL,
    total_trips_rated INT NOT NULL,
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_driver_rating_summary_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF OBJECT_ID('dbo.driver_rating_strength', 'U') IS NULL
CREATE TABLE dbo.driver_rating_strength (
    rating_strength_id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    driver_id UNIQUEIDENTIFIER NOT NULL,
    strength_name NVARCHAR(120) NOT NULL,
    score DECIMAL(3,2) NOT NULL,
    CONSTRAINT FK_driver_rating_strength_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF OBJECT_ID('dbo.driver_cars', 'U') IS NULL
CREATE TABLE dbo.driver_cars (
    car_id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    driver_id UNIQUEIDENTIFIER NOT NULL,
    model NVARCHAR(120) NOT NULL,
    plate_number NVARCHAR(30) NOT NULL,
    color NVARCHAR(40) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_driver_cars_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF OBJECT_ID('dbo.driver_contact_info', 'U') IS NULL
CREATE TABLE dbo.driver_contact_info (
    driver_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    phone NVARCHAR(30) NOT NULL,
    email NVARCHAR(255) NOT NULL,
    address NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_driver_contact_info_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF OBJECT_ID('dbo.driver_trip_feedback', 'U') IS NULL
CREATE TABLE dbo.driver_trip_feedback (
    trip_id NVARCHAR(40) NOT NULL PRIMARY KEY,
    driver_id UNIQUEIDENTIFIER NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    tip_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    comment NVARCHAR(500) NULL,
    updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_driver_trip_feedback_driver
        FOREIGN KEY (driver_id) REFERENCES dbo.driver_onboarding(driver_id)
);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_driver_cars_driver_plate'
      AND object_id = OBJECT_ID('dbo.driver_cars')
)
CREATE UNIQUE INDEX UX_driver_cars_driver_plate
ON dbo.driver_cars(driver_id, plate_number);

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_driver_rating_strength_driver_name'
      AND object_id = OBJECT_ID('dbo.driver_rating_strength')
)
CREATE UNIQUE INDEX UX_driver_rating_strength_driver_name
ON dbo.driver_rating_strength(driver_id, strength_name);
