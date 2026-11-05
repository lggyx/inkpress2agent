import redis

r = redis.Redis(host='localhost', port=26376, decode_responses=True)
keys = r.keys('inkpress:ratelimit:*')
for k in keys:
    r.delete(k)
    print('cleared:', k)
print('done, remaining:', r.keys('inkpress:ratelimit:*'))
