#!/usr/bin/env python3
"""
Manually insert FREE_TIER premium plan
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

def insert_free_tier():
    try:
        print("🔌 Connecting to database...")
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Check if free_tier already exists
        cursor.execute("SELECT id FROM premium_plans WHERE name = 'free_tier'")
        existing = cursor.fetchone()
        
        if existing:
            print(f"✅ FREE_TIER plan already exists with ID: {existing[0]}")
            cursor.close()
            conn.close()
            return 0
        
        # Insert FREE_TIER plan
        now = datetime.now()
        insert_sql = """
        INSERT INTO premium_plans 
        (name, display_name, description, duration_months, price, currency, 
         plan_type, student_discount_percent, features, is_active, max_subscribers,
         created_at, updated_at)
        VALUES 
        (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        RETURNING id;
        """
        
        values = (
            'free_tier',
            'Free Tier',
            'Default free plan with basic access',
            2147483647,  # Integer.MAX_VALUE
            0,
            'VND',
            'FREE_TIER',
            0,
            '["Basic access", "Community participation"]',
            True,
            None,
            now,
            now
        )
        
        cursor.execute(insert_sql, values)
        plan_id = cursor.fetchone()[0]
        conn.commit()
        
        print(f"✅ Successfully created FREE_TIER plan with ID: {plan_id}")
        print(f"   - Name: free_tier")
        print(f"   - Display Name: Free Tier")
        print(f"   - Type: FREE_TIER")
        print(f"   - Price: 0 VND")
        print(f"   - Duration: Permanent (Integer.MAX_VALUE months)")
        
        cursor.close()
        conn.close()
        
        print("\n🎉 Done! FREE_TIER plan is now available.")
        return 0
        
    except psycopg2.Error as e:
        print(f"❌ Database error: {e}")
        return 1
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(insert_free_tier())
