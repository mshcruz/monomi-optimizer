import org.specs2.mutable._

class SQLParserSpec extends Specification {

  "SQLParser" should {
    "parse query1" in {
      val parser = new SQLParser
      val r = parser.parse("""
select
  l_returnflag,
  l_linestatus,
  sum(l_quantity) as sum_qty,
  sum(l_extendedprice) as sum_base_price,
  sum(l_extendedprice * (1 - l_discount)) as sum_disc_price,
  sum(l_extendedprice * (1 - l_discount) * (1 + l_tax)) as sum_charge,
  avg(l_quantity) as avg_qty,
  avg(l_extendedprice) as avg_price,
  avg(l_discount) as avg_disc,
  count(*) as count_order
from
  lineitem
where
  l_shipdate <= date '1998-12-01' - interval '5' day
group by
  l_returnflag,
  l_linestatus
order by
  l_returnflag,
  l_linestatus;""")

      r should beSome
    }

    "parse query2" in {
      val parser = new SQLParser
      val r = parser.parse("""
select
  s_acctbal,
  s_name,
  n_name,
  p_partkey,
  p_mfgr,
  s_address,
  s_phone,
  s_comment
from
  part,
  supplier,
  partsupp,
  nation,
  region
where
  p_partkey = ps_partkey
  and s_suppkey = ps_suppkey
  and p_size = 10
  and p_type like '%foo'
  and s_nationkey = n_nationkey
  and n_regionkey = r_regionkey
  and r_name = 'somename'
  and ps_supplycost = (
    select
      min(ps_supplycost)
    from
      partsupp,
      supplier,
      nation,
      region
    where
      p_partkey = ps_partkey
      and s_suppkey = ps_suppkey
      and s_nationkey = n_nationkey
      and n_regionkey = r_regionkey
      and r_name = 'somename'
  )
order by
  s_acctbal desc,
  n_name,
  s_name,
  p_partkey
limit 100;
""")    
      r should beSome
    }

    "parse query3" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  l_orderkey,
  sum(l_extendedprice * (1 - l_discount)) as revenue,
  o_orderdate,
  o_shippriority
from
  customer,
  orders,
  lineitem
where
  c_mktsegment = 'somesegment'
  and c_custkey = o_custkey
  and l_orderkey = o_orderkey
  and o_orderdate < date '1999-01-01'
  and l_shipdate > date '1999-01-01'
group by
  l_orderkey,
  o_orderdate,
  o_shippriority
order by
  revenue desc,
  o_orderdate
limit 10;
""")
      r should beSome
    }

    "parse query4" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  o_orderpriority,
  count(*) as order_count
from
  orders
where
  o_orderdate >= date '1999-01-01'
  and o_orderdate < date '1999-01-01' + interval '3' month
  and exists (
    select
      *
    from
      lineitem
    where
      l_orderkey = o_orderkey
      and l_commitdate < l_receiptdate
  )
group by
  o_orderpriority
order by
  o_orderpriority;
""")
      r should beSome
    }

    "parse query5" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  n_name,
  sum(l_extendedprice * (1 - l_discount)) as revenue
from
  customer,
  orders,
  lineitem,
  supplier,
  nation,
  region
where
  c_custkey = o_custkey
  and l_orderkey = o_orderkey
  and l_suppkey = s_suppkey
  and c_nationkey = s_nationkey
  and s_nationkey = n_nationkey
  and n_regionkey = r_regionkey
  and r_name = 'foo'
  and o_orderdate >= date '1999-01-01'
  and o_orderdate < date '1999-01-01' + interval '1' year
group by
  n_name
order by
  revenue desc;
""")
      r should beSome
    }

    "parse query6" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  sum(l_extendedprice * l_discount) as revenue
from
  lineitem
where
  l_shipdate >= date '1999-01-01'
  and l_shipdate < date '1999-01-01' + interval '1' year
  and l_discount between 2 - 0.01 and 2 + 0.01
  and l_quantity < 3;
""")
      r should beSome
    }

    "parse query7" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  supp_nation,
  cust_nation,
  l_year,
  sum(volume) as revenue
from
  (
    select
      n1.n_name as supp_nation,
      n2.n_name as cust_nation,
      extract(year from l_shipdate) as l_year,
      l_extendedprice * (1 - l_discount) as volume
    from
      supplier,
      lineitem,
      orders,
      customer,
      nation n1,
      nation n2
    where
      s_suppkey = l_suppkey
      and o_orderkey = l_orderkey
      and c_custkey = o_custkey
      and s_nationkey = n1.n_nationkey
      and c_nationkey = n2.n_nationkey
      and (
        (n1.n_name = 'a' and n2.n_name = 'b')
        or (n1.n_name = 'b' and n2.n_name = 'a')
      )
      and l_shipdate between date '1995-01-01' and date '1996-12-31'
  ) as shipping
group by
  supp_nation,
  cust_nation,
  l_year
order by
  supp_nation,
  cust_nation,
  l_year;
""")
      r should beSome
    }

    "parse query8" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  o_year,
  sum(case
    when nation = 'nation' then volume
    else 0
  end) / sum(volume) as mkt_share
from
  (
    select
      extract(year from o_orderdate) as o_year,
      l_extendedprice * (1 - l_discount) as volume,
      n2.n_name as nation
    from
      part,
      supplier,
      lineitem,
      orders,
      customer,
      nation n1,
      nation n2,
      region
    where
      p_partkey = l_partkey
      and s_suppkey = l_suppkey
      and l_orderkey = o_orderkey
      and o_custkey = c_custkey
      and c_nationkey = n1.n_nationkey
      and n1.n_regionkey = r_regionkey
      and r_name = 'rname'
      and s_nationkey = n2.n_nationkey
      and o_orderdate between date '1995-01-01' and date '1996-12-31'
      and p_type = 'ptype'
  ) as all_nations
group by
  o_year
order by
  o_year;
""")
      r should beSome
    }

    "parse query9" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
  nation,
  o_year,
  sum(amount) as sum_profit
from
  (
    select
      n_name as nation,
      extract(year from o_orderdate) as o_year,
      l_extendedprice * (1 - l_discount) - ps_supplycost * l_quantity as amount
    from
      part,
      supplier,
      lineitem,
      partsupp,
      orders,
      nation
    where
      s_suppkey = l_suppkey
      and ps_suppkey = l_suppkey
      and ps_partkey = l_partkey
      and p_partkey = l_partkey
      and o_orderkey = l_orderkey
      and s_nationkey = n_nationkey
      and p_name like '%sky%'
  ) as profit
group by
  nation,
  o_year
order by
  nation,
  o_year desc;
""")
      r should beSome
    }

    "parse query10" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
	c_custkey,
	c_name,
	sum(l_extendedprice * (1 - l_discount)) as revenue,
	c_acctbal,
	n_name,
	c_address,
	c_phone,
	c_comment
from
	customer,
	orders,
	lineitem,
	nation
where
	c_custkey = o_custkey
	and l_orderkey = o_orderkey
	and o_orderdate >= date '1999-01-01'
	and o_orderdate < date '1999-01-01' + interval '3' month
	and l_returnflag = 'R'
	and c_nationkey = n_nationkey
group by
	c_custkey,
	c_name,
	c_acctbal,
	c_phone,
	n_name,
	c_address,
	c_comment
order by
	revenue desc
""")
      r should beSome
    }

    "parse query11" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
	ps_partkey,
	sum(ps_supplycost * ps_availqty) as value
from
	partsupp,
	supplier,
	nation
where
	ps_suppkey = s_suppkey
	and s_nationkey = n_nationkey
	and n_name = 'nnation'
group by
	ps_partkey having
		sum(ps_supplycost * ps_availqty) > (
			select
				sum(ps_supplycost * ps_availqty) * 300
			from
				partsupp,
				supplier,
				nation
			where
				ps_suppkey = s_suppkey
				and s_nationkey = n_nationkey
				and n_name = 'name'
		)
order by
	value desc;
""")
      r should beSome
    }

    "parse query12" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
	l_shipmode,
	sum(case
		when o_orderpriority = '1-URGENT'
			or o_orderpriority = '2-HIGH'
			then 1
		else 0
	end) as high_line_count,
	sum(case
		when o_orderpriority <> '1-URGENT'
			and o_orderpriority <> '2-HIGH'
			then 1
		else 0
	end) as low_line_count
from
	orders,
	lineitem
where
	o_orderkey = l_orderkey
	and l_shipmode in ('mode0', 'mode1')
	and l_commitdate < l_receiptdate
	and l_shipdate < l_commitdate
	and l_receiptdate >= date '1998-01-01'
	and l_receiptdate < date '1998-01-01' + interval '1' year
group by
	l_shipmode
order by
	l_shipmode;
""")
      r should beSome
    }

    "parse query13" in {
      val parser = new SQLParser
      val r = parser.parse(
"""
select
	c_count,
	count(*) as custdist
from
	(
		select
			c_custkey,
			count(o_orderkey)
		from
			customer left outer join orders on
				c_custkey = o_custkey
				and o_comment not like '%:1%:2%'
		group by
			c_custkey
	) as c_orders (c_custkey, c_count)
group by
	c_count
order by
	custdist desc,
	c_count desc;
""")
      r should beSome
    }

  }
}