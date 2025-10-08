#!/usr/bin/env python3
"""
Test if we can insert FREE_TIER into premium_plans table
"""

import psycopg2
from datetime import datetime
import sys

DB_CONFIG = {
    'host': '221.132.33.141',
    'port': 5432,
    'database': 'skillverse_db',
    'user': 'skillverse_user',
    'password': '12345'
}

def test_insert():
    try:
        print("🔌 Connecting to database...")
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Try to insert a test FREE_TIER plan
        test_sql = """
        INSERT INTO premium_plans 
        (name, display_name, description, duration_months, price, currency, 
         plan_type, student_discount_percent, features, is_active, 
         created_at, updated_at)
        VALUES 
        ('test_free_tier', 'Test Free Tier', 'Test description', 999, 0, 'VND',
         'FREE_TIER', 0, '["test"]', true,
         %s, %s)
        RETURNING id;
        """
        
        now = datetime.now()
        cursor.execute(test_sql, (now, now))
        
        inserted_id = cursor.fetchone()[0]
        print(f"✅ Successfully inserted FREE_TIER plan with ID: {inserted_id}")
        
        # Clean up test data
        cursor.execute("DELETE FROM premium_plans WHERE id = %s", (inserted_id,))
        conn.commit()
        
        print("🧹 Cleaned up test data")
        print("\n🎉 Test passed! FREE_TIER constraint is working correctly.")
        
        cursor.close()
        conn.close()
        
        return 0
        
    except psycopg2.Error as e:
        print(f"❌ Database error: {e}")
        return 1
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(test_insert())
