package ua.kiev.prog.case2;

import ua.kiev.prog.shared.Id;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDAO<T> {
    private final Connection conn;
    private final String table;

    public AbstractDAO(Connection conn, String table) {
        this.conn = conn;
        this.table = table;
    }

    public void createTable(Class<T> cls) {
        Field[] fields = cls.getDeclaredFields();
        Field id = getPrimaryKeyField(null, fields);

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ")
                .append(table)
                .append("(");

        sql.append(id.getName())
                .append(" ")
                .append(" INT AUTO_INCREMENT PRIMARY KEY,");

        for (Field f : fields) {
            if (f != id) {
                f.setAccessible(true);

                sql.append(f.getName()).append(" ");

                if (f.getType() == int.class) {
                    sql.append("INT,");
                } else if (f.getType() == String.class) {
                    sql.append("VARCHAR(100),");
                } else
                    throw new RuntimeException("Wrong type");
            }
        }

        sql.deleteCharAt(sql.length() - 1);
        sql.append(")");

        try {
            try (Statement st = conn.createStatement()) {
                st.execute(sql.toString());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void add(T t) {
        try {
            Field[] fields = t.getClass().getDeclaredFields();
            Field id = getPrimaryKeyField(t, fields);

            StringBuilder names = new StringBuilder();
            StringBuilder values = new StringBuilder();

            // insert into t (name,age) values("..",..
//            String name = "";
            for (Field f : fields) {
                if (f != id) {
                    f.setAccessible(true);
//                    if(f.getName().equals("name")){
//                        name = (String) f.get(t);
//                    }
                    names.append(f.getName()).append(',');
                    values.append('"').append(f.get(t)).append("\",");
                }
            }

            names.deleteCharAt(names.length() - 1); // last ','
            values.deleteCharAt(values.length() - 1);

            String sql = "INSERT INTO " + table + "(" + names.toString() +
                    ") VALUES(" + values.toString() + ")";

            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }

            int intId = getId(); // Dz#1
            id.setAccessible(true);
            id.set(t, intId);

            // TODO: get ID - done
            // SELECT - X

        } catch (Exception ex) {

            throw new RuntimeException(ex);
        }
    }


    public void update(T t) {
        try {
            Field[] fields = t.getClass().getDeclaredFields();
            Field id = getPrimaryKeyField(t, fields);

            StringBuilder sb = new StringBuilder();

            for (Field f : fields) {
                if (f != id) {
                    f.setAccessible(true);

                    sb.append(f.getName())
                            .append(" = ")
                            .append('"')
                            .append(f.get(t))
                            .append('"')
                            .append(',');
                }
            }

            sb.deleteCharAt(sb.length() - 1);

            // update t set name = "aaaa", age = "22" where id = 5
            String sql = "UPDATE " + table + " SET " + sb.toString() + " WHERE " +
                    id.getName() + " = \"" + id.get(t) + "\"";

            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void delete(T t) {
        try {
            Field[] fields = t.getClass().getDeclaredFields();
            Field id = getPrimaryKeyField(t, fields);

            String sql = "DELETE FROM " + table + " WHERE " + id.getName() +
                    " = \"" + id.get(t) + "\"";

            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<T> getAll(Class<T> cls) {
        List<T> res = new ArrayList<>();

        try {
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                    ResultSetMetaData md = rs.getMetaData();

                    while (rs.next()) {
                        T t = cls.getDeclaredConstructor().newInstance(); //!!!

                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            String columnName = md.getColumnName(i);
                            Field field = cls.getDeclaredField(columnName);
                            field.setAccessible(true);

                            field.set(t, rs.getObject(columnName));
                        }

                        res.add(t);
                    }
                }
            }

            return res;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    //Dz#2 >>>
    public List<T> getAll(Class<T> cls, String... names) {
        List<T> res = new ArrayList<>();
//        String pres = countPreStatements(names.length);
        String sql = String.format("SELECT%s FROM " + table, addValues(names));

        try {
            try (Statement st = conn.createStatement()) {
//                for (int i = 1; i <= names.length; i++) {
//                    st.setString(i, names[i - 1]);
//                }

                try (ResultSet rs = st.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();

                    while (rs.next()) {
                        T t = cls.getDeclaredConstructor().newInstance(); //!!!

                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            String columnName = md.getColumnName(i);
                            Field field = cls.getDeclaredField(columnName);
                            field.setAccessible(true);

                            field.set(t, rs.getObject(columnName));
                        }

                        res.add(t);
                    }
                }
            }
            return res;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String countPreStatements(int number) {
        String res = "";
        switch (number) {
            case 1 -> res = "?";
            case 2 -> res = "?, ?";
            case 3 -> res = "?, ?, ?";
            case 4 -> res = "?, ?, ?, ?";
            case 5 -> res = "?, ?, ?, ?, ?";
        }
        return res;
    }

    private String addValues(String[] names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            sb.append(" " + names[i] + ",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private Field getPrimaryKeyField(T t, Field[] fields) {
        Field result = null;

        for (Field f : fields) {
            if (f.isAnnotationPresent(Id.class)) {
                result = f;
                result.setAccessible(true);
                break;
            }
        }

        if (result == null)
            throw new RuntimeException("No Id field found");

        return result;
    }

    private int getId() {
        String getId = "SELECT MAX(`id`) FROM Clients";
        //  or          String getId = "SELECT id FROM Clients WHERE name = '" + name + "'";
        int intId = 0;
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(getId)) {
                if (rs.next()) {
                    intId = rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return intId;
    }
}