package megamek.server;

public interface ITeamOverviewModel {
    private int rowCount;
    private int columnCount;

    public ITeamOverviewModel(int rowCount, int columnCount) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
    }

    public boolean seeTeam(int team) {}

    public Class<?> getColumnClass(int columnIndex) {}

    public void updateTable(Game game) {}

    public void clearData() {}

    public String getColumnName(int column) {}

    public void updateRowHeight(int row) {}

    public int classIndex(Entity entity) {}

    public String relativeValue(ArrayList<Long> values, int index) {}

    public String unitSummary(int[] values, boolean[] row, boolean[] columns) {}

    public Object getValueAt(int row, int column) {}
    
    public void getTeamInfo(Game game) {}
}