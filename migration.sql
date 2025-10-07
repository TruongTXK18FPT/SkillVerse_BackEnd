-- Migration script to add module_id column to assignments table
-- Run this on your PostgreSQL database

-- Check if column exists before adding
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'assignments' 
        AND column_name = 'module_id'
    ) THEN
        -- Add module_id column
        ALTER TABLE assignments 
        ADD COLUMN module_id BIGINT;
        
        -- Add NOT NULL constraint after setting default values (if needed)
        -- First, you might need to set a default module for existing assignments
        -- UPDATE assignments SET module_id = 1 WHERE module_id IS NULL;
        
        -- Then add NOT NULL constraint
        -- ALTER TABLE assignments ALTER COLUMN module_id SET NOT NULL;
        
        -- Add foreign key constraint
        ALTER TABLE assignments 
        ADD CONSTRAINT fk_assignments_module 
        FOREIGN KEY (module_id) 
        REFERENCES modules(id) 
        ON DELETE CASCADE;
        
        -- Add index for better query performance
        CREATE INDEX idx_assignments_module_id ON assignments(module_id);
        
        RAISE NOTICE 'Column module_id added to assignments table successfully';
    ELSE
        RAISE NOTICE 'Column module_id already exists in assignments table';
    END IF;
END $$;
