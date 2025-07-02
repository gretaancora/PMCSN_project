import pandas as pd
import matplotlib.pyplot as plt

def plot_transient(csv_path, center_idx):
    df = pd.read_csv(csv_path)
    plt.figure()
    # tutte le repliche insieme: mostriamo media e confidence band
    mean_ts = df.groupby('Time')['ETs'].mean()
    std_ts  = df.groupby('Time')['ETs'].std()
    plt.plot(mean_ts.index, mean_ts.values, label='Media')
    plt.fill_between(
        mean_ts.index,
        mean_ts - 1.96 * std_ts / (len(df['Run'].unique())**0.5),
        mean_ts + 1.96 * std_ts / (len(df['Run'].unique())**0.5),
        alpha=0.3
    )
    plt.title(f"Center {center_idx} – Simulazione Finita")
    plt.xlabel('Time (s)')
    plt.ylabel('E[T_S]')
    plt.legend()
    plt.savefig(f"img/center{center_idx}_finite.png")
    plt.close()

def plot_stationary(csv_path, center_idx, max_batches=128, stride=2):
    df = pd.read_csv(csv_path)
    plt.figure()
    df = df.iloc[:max_batches]
    cum_mean = df['ETs'].expanding().mean()[::stride]
    plt.yscale('log')
    plt.plot(df['Batch'][::stride], cum_mean)
    plt.title(f"Center {center_idx} – Batch‐Means")
    plt.xlabel('Batch Index')
    plt.ylabel('Media Cumulativa di E[T_S]')
    plt.savefig(f"img/center{center_idx}_infinite.png")
    plt.close()

if __name__ == "__main__":
    import sys
    mode = sys.argv[1]  # "finite" o "infinite"
    system = sys.argv[2]  # "classic" o "ridesharing"
    # es: python plot_compare.py finite classic
    for i in range( len(4) ): #aggiorna la len o 3 o 4 nodi
        path = f"resources/results/{mode}_center{i}.csv"
    if mode == "finite":
        plot_transient(path, i)
    else:
        plot_stationary(path, i)
