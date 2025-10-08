#!/usr/bin/env python3
"""
Fix premium_plans constraint to include FREE_TIER
Run this script to update the database constraint
"""

import psycopg2
import sys

# Database configuration
DB_CONFIG = {
    'host': '221.132.33.141',
    'port': 5432,
    'database': 'skillverse_db',
    'user': 'skillverse_user',
    'password': '12345'
}

SQL_FIX = """
-- Drop existing constraint
ALTER TABLE premium_plans DROP CONSTRAINT IF EXISTS premium_plans_plan_type_check;

-- Add corrected constraint with all enum values
ALTER TABLE premium_plans 
ADD CONSTRAINT premium_plans_plan_type_check 
CHECK (plan_type IN ('FREE_TIER', 'PREMIUM_BASIC', 'PREMIUM_PLUS', 'STUDENT_PACK'));
"""

def main():
    try:
        print("🔌 Connecting to database...")
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        print("🔧 Executing SQL fix...")
        cursor.execute(SQL_FIX)
        conn.commit()
        
        print("✅ Constraint fixed successfully!")
        
        # Verify the fix
        cursor.execute("""
            SELECT constraint_name, check_clause 
            FROM information_schema.check_constraints 
            WHERE constraint_name = 'premium_plans_plan_type_check'
        """)
        
        result = cursor.fetchone()
        if result:
            print(f"✅ Verified constraint: {result[0]}")
            print(f"   Check clause: {result[1]}")
        else:
            print("⚠️ Could not verify constraint")
        
        cursor.close()
        conn.close()
        
        print("\n🎉 Done! You can now restart your application.")
        return 0
        
    except psycopg2.Error as e:
        print(f"❌ Database error: {e}")
        return 1
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(main())
