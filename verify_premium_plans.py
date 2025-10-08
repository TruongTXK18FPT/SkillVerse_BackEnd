#!/usr/bin/env python3
"""
Verify that all premium plans including FREE_TIER were initialized
"""

import psycopg2
import json

DB_CONFIG = {
    'host': '221.132.33.141',
    'port': 5432,
    'database': 'skillverse_db',
    'user': 'skillverse_user',
    'password': '12345'
}

def verify_plans():
    try:
        print("🔌 Connecting to database...")
        conn = psycopg2.connect(**DB_CONFIG)
        cursor = conn.cursor()
        
        # Get all premium plans
        cursor.execute("""
            SELECT id, name, plan_type, price, duration_months, is_active
            FROM premium_plans
            ORDER BY 
                CASE plan_type 
                    WHEN 'FREE_TIER' THEN 1
                    WHEN 'PREMIUM_BASIC' THEN 2
                    WHEN 'PREMIUM_PLUS' THEN 3
                    WHEN 'STUDENT_PACK' THEN 4
                END
        """)
        
        plans = cursor.fetchall()
        
        print("\n📊 Premium Plans in Database:\n")
        print(f"{'ID':<5} {'Name':<20} {'Type':<20} {'Price':<12} {'Months':<8} {'Active'}")
        print("=" * 85)
        
        plan_types_found = set()
        for plan in plans:
            plan_id, name, plan_type, price, months, active = plan
            plan_types_found.add(plan_type)
            status = "✅" if active else "❌"
            print(f"{plan_id:<5} {name:<20} {plan_type:<20} {price:<12} {months:<8} {status}")
        
        print("\n" + "=" * 85)
        
        # Check all required plan types
        required_types = {'FREE_TIER', 'PREMIUM_BASIC', 'PREMIUM_PLUS', 'STUDENT_PACK'}
        missing_types = required_types - plan_types_found
        
        if missing_types:
            print(f"\n⚠️  Missing plan types: {', '.join(missing_types)}")
        else:
            print(f"\n✅ All {len(required_types)} required plan types are present!")
        
        # Check constraint
        cursor.execute("""
            SELECT check_clause 
            FROM information_schema.check_constraints 
            WHERE constraint_name = 'premium_plans_plan_type_check'
        """)
        
        constraint = cursor.fetchone()
        if constraint:
            print(f"\n🔒 Constraint Check Clause:")
            print(f"   {constraint[0]}")
        
        cursor.close()
        conn.close()
        
        print("\n🎉 Verification complete!")
        
    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    verify_plans()
