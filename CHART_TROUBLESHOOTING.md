# Chart Display Troubleshooting Guide

## How to Debug the Chart Issues

### Step 1: Check Logcat Output

Run the app and navigate to the Transaction screen. Look for these debug messages in Logcat:

**For Transaction Screen:**
```
📊 DEBUG: Loading all chart data...
  ⏳ Loading monthly chart...
  ⏳ Loading line chart data...
  ⏳ Loading category summary...
  ⏳ Loading monthly comparison...
  ⏳ Loading top categories...
  ⏳ Loading average spending...
  ✅ Monthly chart loaded: X months, Y days
  ✅ Line chart loaded: Y days
  ✅ Category summary: X expense categories
  ✅ Monthly comparison: X categories
  ✅ Top categories: X categories
  ✅ Average spending: X categories
  ✅ Forecasts loaded - Savings: true/false, Spending: true/false, Suggestions: X
```

**For Goal Screen:**
```
📊 DEBUG: Loading savings trends for 6 months...
✅ DEBUG: Savings trends loaded successfully - X months
OR
❌ DEBUG: Savings trends failed - [error message]
```

### Step 2: Identify the Problem

#### Problem A: All counts are 0
**Example Output:**
```
✅ Monthly chart loaded: 0 months, 0 days
✅ Category summary: 0 expense categories
```

**Cause:** The API is returning empty data or the backend endpoints are not returning data.

**Solution:**
1. Check if you have transactions in your database
2. Verify the backend API endpoints are working:
   - `GET /api/analytics/spending-trends?months=12`
   - `GET /api/analytics/category-summary?start_date=XXX&end_date=XXX`
   - `GET /api/analytics/monthly-comparison?month=2024-01`
   - `GET /api/analytics/top-categories/current-month`
   - `GET /api/analytics/average-spending?period=month`

#### Problem B: Error messages in logs
**Example Output:**
```
❌ DEBUG: Savings trends failed - API error: 404
```

**Cause:** The API endpoint doesn't exist or authentication failed.

**Solution:**
1. Check your backend server is running
2. Verify the API endpoints match what the backend provides
3. Check authentication token is valid

#### Problem C: Exception/Crash
**Example Output:**
```
❌ DEBUG: Savings trends exception - Connection refused
java.net.ConnectException: Failed to connect to...
```

**Cause:** Network connectivity issue or wrong server URL.

**Solution:**
1. Check your network connection
2. Verify the base URL in your Retrofit configuration
3. Make sure backend server is accessible

### Step 3: Quick Fixes

#### Fix 1: Check if you have data
The charts won't show if there's no transaction data. Make sure you:
1. Have created some transactions in the app
2. Those transactions have valid dates within the last 12 months
3. Transactions are linked to categories

#### Fix 2: Check Network/API
1. Open Postman or similar tool
2. Test these endpoints with your auth token:
   ```
   GET http://your-backend-url/api/analytics/spending-trends?months=12
   GET http://your-backend-url/api/analytics/savings-trends?months=6
   ```
3. Check if they return valid JSON data

#### Fix 3: Check the API response structure
The backend might be returning data in a different format than expected. Check the response matches:

**Spending Trends Response:**
```json
{
  "monthly_summary": [
    {
      "year": 2024,
      "month": 1,
      "total_spent": 1000.0,
      "total_income": 2000.0,
      "month_name": "January",
      "display_name": "Jan 2024"
    }
  ],
  "summary": {...},
  "analysis_period": {...}
}
```

**Savings Trends Response:**
```json
{
  "monthly_trends": [
    {
      "year": 2024,
      "month": 1,
      "display_name": "Jan 2024",
      "saved_amount": 500.0,
      "target_amount": 1000.0,
      "achievement_rate": 50.0
    }
  ],
  "months_analyzed": 6,
  "analysis_period": {...}
}
```

### Step 4: Enable Verbose Logging

If you still can't see what's wrong, add this to check the actual API responses:

In `TransactionRepositoryImpl.kt`, add logging:

```kotlin
override suspend fun getSpendingTrends(months: Int): Result<List<SpendingTrendData>> {
    return try {
        val response = apiService.getSpendingTrends(months)
        println("🔍 API Response Code: ${response.code()}")
        println("🔍 API Response Body: ${response.body()}")
        println("🔍 API Error Body: ${response.errorBody()?.string()}")
        
        if (response.isSuccessful) {
            // ... rest of the code
        }
    }
}
```

### Step 5: Common Issues & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Charts show "No data" | Empty transaction database | Add some transactions first |
| Error 401 | Invalid/expired token | Re-login to get new token |
| Error 404 | Wrong API endpoint | Check endpoint URLs match backend |
| Error 500 | Backend server error | Check backend logs |
| Charts not loading at all | Network issue | Check internet connection |
| Only some charts work | Partial API implementation | Check which endpoints are implemented |

### Step 6: Test With Sample Data

If you want to test the UI without backend data, you can temporarily use sample data:

In `GetMonthlyChartDataUseCase.kt`, temporarily replace the execute method to return sample data:

```kotlin
suspend fun execute(months: Int = 12): MonthlyChartData {
    // TEMPORARY: Return sample data for testing
    return MonthlyChartData(
        months = listOf(
            MonthlyData("Jan", 5000.0, 3000.0, 1),
            MonthlyData("Feb", 6000.0, 4000.0, 2),
            MonthlyData("Mar", 5500.0, 3500.0, 3)
        ),
        days = emptyList(),
        selectedFilter = ChartFilter.EXPENSES,
        selectedPeriod = PeriodFilter.YEAR
    )
}
```

## Expected Behavior

Once everything is working correctly:

### Transaction Screen should show:
1. ✅ Monthly bar chart (income/expenses by month)
2. ✅ Line chart (daily income vs expenses)
3. ✅ Pie chart (expense breakdown by category)
4. ✅ Donut chart (top categories)
5. ✅ Radar chart (average spending)
6. ✅ Spending forecast chart (NEW)
7. ✅ AI suggestions card (NEW)

### Goal Screen should show:
1. ✅ Savings goal section
2. ✅ Savings trends chart (monthly savings progress)
3. ✅ Savings forecast chart (NEW - future projection)
4. ✅ Budget and category limits

## Next Steps

After checking the logs, share with me:
1. The logcat output when you navigate to Transaction screen
2. The logcat output when you navigate to Goal screen
3. Any error messages you see

This will help me pinpoint the exact issue!
