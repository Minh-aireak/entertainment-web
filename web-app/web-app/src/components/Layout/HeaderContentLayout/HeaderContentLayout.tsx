import Header from "../../Header/Header";

function HeaderContentLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="default-layout">
      <Header />
      <div className="layout-content">
        <main>{children}</main>
      </div>
    </div>
  );
}

export default HeaderContentLayout;
